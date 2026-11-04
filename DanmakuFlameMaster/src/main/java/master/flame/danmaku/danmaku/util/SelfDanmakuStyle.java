package master.flame.danmaku.danmaku.util;

import java.util.Locale;
import java.util.zip.CRC32;
import master.flame.danmaku.danmaku.model.BaseDanmaku;

/** Compare only the signed-in account's hash; never infer another sender's UID. */
public final class SelfDanmakuStyle {
    public static final int BORDER_COLOR = 0xfffe679a;
    private SelfDanmakuStyle() {}

    public static String senderHash(long mid) {
        if (mid <= 0) return "";
        CRC32 crc = new CRC32();
        String digits = Long.toString(mid);
        for (int i = 0; i < digits.length(); i++) crc.update(digits.charAt(i));
        return Long.toHexString(crc.getValue());
    }

    public static boolean sameHash(String expected, String actual) {
        String a = normalize(expected), b = normalize(actual);
        return !a.isEmpty() && a.equals(b);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String hash = value.trim().toLowerCase(Locale.US);
        if (hash.isEmpty() || hash.length() > 8) return "";
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) return "";
        }
        int start = 0;
        while (start < hash.length() - 1 && hash.charAt(start) == '0') start++;
        return hash.substring(start);
    }

    public static void apply(BaseDanmaku item) {
        item.borderColor = BORDER_COLOR;
        item.padding = Math.max(2, item.padding);
    }

    public static void identify(BaseDanmaku item, String ownHash, String senderHash) {
        item.userHash = senderHash;
        if (sameHash(ownHash, senderHash)) apply(item);
    }
}
