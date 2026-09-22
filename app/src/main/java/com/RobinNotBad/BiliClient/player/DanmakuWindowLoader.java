package com.RobinNotBad.BiliClient.player;

import com.RobinNotBad.BiliClient.model.DmSegMobileReply;
import com.RobinNotBad.BiliClient.util.RequestCancellation;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** One serial worker, with memory and requests bounded by the playback window. */
public final class DanmakuWindowLoader {
    public static final long SEGMENT_MS = 360000L;
    private static final long RETRY_DELAY_MS = 15000L;

    public interface Source {
        DmSegMobileReply load(int index, RequestCancellation cancellation) throws Exception;
    }

    public interface Listener {
        void onWindow(long revision, Map<Integer, DmSegMobileReply> segments);
    }

    public interface Clock {
        long now();
    }

    private final Executor executor;
    private final Source source;
    private final Listener listener;
    private final Clock clock;
    private final Map<Integer, DmSegMobileReply> cache = new LinkedHashMap<>();
    private final Map<Integer, Long> retryAt = new HashMap<>();
    private int current;
    private int last;
    private long revision;
    private boolean running;
    private boolean closed;
    private RequestCancellation activeRequest;

    public DanmakuWindowLoader(Executor executor, Source source, Listener listener, Clock clock) {
        this.executor = executor;
        this.source = source;
        this.listener = listener;
        this.clock = clock;
    }

    public synchronized void update(long positionMs, long durationMs) {
        if (closed) return;
        int end = (int) Math.max(1L, Math.min(Integer.MAX_VALUE,
                (Math.max(1L, durationMs) - 1L) / SEGMENT_MS + 1L));
        int target = (int) Math.min(end, Math.max(0L, positionMs) / SEGMENT_MS + 1L);
        if (current != target || last != end) {
            current = target;
            last = end;
            revision++;
            if (activeRequest != null) activeRequest.cancel();
            for (Iterator<Integer> it = cache.keySet().iterator(); it.hasNext();) {
                if (!inWindow(it.next())) it.remove();
            }
            for (Iterator<Integer> it = retryAt.keySet().iterator(); it.hasNext();) {
                if (!inWindow(it.next())) it.remove();
            }
            publish();
        }
        if (!running && nextSegment() != 0) {
            running = true;
            executor.execute(this::drain);
        }
    }

    public synchronized boolean isCurrent(long expectedRevision) {
        return !closed && revision == expectedRevision;
    }

    public synchronized void close() {
        closed = true;
        revision++;
        if (activeRequest != null) activeRequest.cancel();
        cache.clear();
        retryAt.clear();
    }

    private boolean inWindow(int index) {
        return index >= 1 && index <= last && Math.abs((long) index - current) <= 1L;
    }

    private int nextSegment() {
        int[] priority = {current, current < last ? current + 1 : 0, current - 1};
        for (int index : priority) {
            Long retry = retryAt.get(index);
            if (inWindow(index) && !cache.containsKey(index)
                    && (retry == null || clock.now() >= retry)) return index;
        }
        return 0;
    }

    private void publish() {
        listener.onWindow(revision, Collections.unmodifiableMap(new LinkedHashMap<>(cache)));
    }

    private void drain() {
        while (true) {
            final int index;
            final long requestRevision;
            final RequestCancellation cancellation;
            synchronized (this) {
                index = closed ? 0 : nextSegment();
                if (index == 0) {
                    running = false;
                    activeRequest = null;
                    return;
                }
                requestRevision = revision;
                cancellation = new RequestCancellation();
                activeRequest = cancellation;
            }
            DmSegMobileReply result = null;
            for (int attempt = 0; attempt < 2 && !cancellation.isCancelled(); attempt++) {
                try {
                    result = source.load(index, cancellation);
                    if (result == null) throw new IOException("Missing danmaku response");
                    break;
                } catch (Exception ignored) {
                    // Failed segments remain retryable; valid empty segments are cached.
                }
            }
            synchronized (this) {
                activeRequest = null;
                if (closed || revision != requestRevision) continue;
                if (result == null) {
                    retryAt.put(index, clock.now() + RETRY_DELAY_MS);
                } else {
                    cache.put(index, result);
                    retryAt.remove(index);
                    publish();
                }
            }
        }
    }
}
