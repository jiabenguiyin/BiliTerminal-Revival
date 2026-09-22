package com.RobinNotBad.BiliClient.util;

import java.io.InterruptedIOException;

import okhttp3.Call;

/** Cancellation belongs to one request, never to the shared HTTP client. */
public final class RequestCancellation {
    private boolean cancelled;
    private Call call;

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized void attach(Call next) throws InterruptedIOException {
        if (cancelled) {
            next.cancel();
            throw new InterruptedIOException("Request cancelled");
        }
        call = next;
    }

    public synchronized void cancel() {
        cancelled = true;
        if (call != null) call.cancel();
        call = null;
    }
}
