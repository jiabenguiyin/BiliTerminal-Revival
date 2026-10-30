package com.RobinNotBad.BiliClient.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InterruptedIOException;

/** Streaming count only, stopping as soon as the protection threshold is exceeded. */
public final class DanmakuXmlCounter {
    private DanmakuXmlCounter() { }

    public static long count(File file, int stopAt, RequestCancellation cancellation) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        try (FileInputStream stream = new FileInputStream(file)) {
            parser.setInput(stream, null);
            long count = 0;
            while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
                if (cancellation != null && cancellation.isCancelled())
                    throw new InterruptedIOException("Danmaku count cancelled");
                if (parser.getEventType() == XmlPullParser.START_TAG && "d".equals(parser.getName())) {
                    if (++count >= stopAt) return count;
                }
            }
            return count;
        }
    }
}
