package com.RobinNotBad.BiliClient.util;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/** Login JSON and raw Cookie headers; errors never include their contents. */
public final class LoginTextFile {
    public static final int MAX_BYTES = 64 * 1024;
    private LoginTextFile() {}

    public static final class Login {
        public final String cookies, refreshToken;
        private Login(String cookies, String refreshToken) {
            this.cookies = cookies; this.refreshToken = refreshToken;
        }
    }

    public static Login parse(String text) throws IOException {
        if (text == null || text.length() > MAX_BYTES) throw invalid();
        String normalized = text.trim();
        if (normalized.startsWith("\uFEFF")) normalized = normalized.substring(1).trim();
        try {
            JSONObject json = normalized.startsWith("{") ? new JSONObject(normalized)
                    : new JSONObject().put("cookies", normalized.replaceFirst("(?i)^cookie:\\s*", ""));
            Object cookie = json.get("cookies");
            Object refresh = json.opt("refresh_token");
            if (!(cookie instanceof String) || (refresh != null && !(refresh instanceof String))) throw invalid();
            String raw = ((String) cookie).trim();
            if (raw.indexOf('\r') >= 0 || raw.indexOf('\n') >= 0) throw invalid();
            Cookies parsed = new Cookies(raw);
            // Preserve token bytes, especially percent escapes and '+'; only normalize names.
            for (String name : new String[]{"SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5"}) {
                parsed.canonicalize(name);
            }
            String cookies = parsed.toString();
            if (!AccountManager.hasSession(cookies, AccountManager.cookieMid(cookies))) throw invalid();
            return new Login(cookies, refresh == null ? "" : (String) refresh);
        } catch (Exception ignored) {
            throw invalid();
        }
    }

    public static String encode(String cookies, String refreshToken) throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("cookies", cookies);
            json.put("refresh_token", refreshToken == null ? "" : refreshToken);
            String text = json.toString();
            parse(text);
            if (text.getBytes("UTF-8").length > MAX_BYTES) throw invalid();
            return text;
        } catch (Exception ignored) { throw invalid(); }
    }

    /** The caller owns and closes the stream. Accept Notepad UTF-8/UTF-16 BOMs. */
    public static String read(InputStream input) throws IOException {
        if (input == null) throw new IOException("无法打开文本文件");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new IOException("文本文件过大，请选择登录信息文本（最大64KB）");
            bytes.write(buffer, 0, count);
        }
        byte[] data = bytes.toByteArray();
        String charset = "UTF-8";
        int offset = 0;
        if (data.length >= 3 && data[0] == (byte)0xEF && data[1] == (byte)0xBB && data[2] == (byte)0xBF) offset = 3;
        else if (data.length >= 2 && data[0] == (byte)0xFF && data[1] == (byte)0xFE) { charset = "UTF-16LE"; offset = 2; }
        else if (data.length >= 2 && data[0] == (byte)0xFE && data[1] == (byte)0xFF) { charset = "UTF-16BE"; offset = 2; }
        String text;
        try {
            text = Charset.forName(charset).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data, offset, data.length - offset)).toString();
        } catch (CharacterCodingException ignored) { throw new IOException("请使用UTF-8或带BOM的UTF-16文本文件"); }
        Login login = parse(text);
        return encode(login.cookies, login.refreshToken);
    }

    public static void write(OutputStream output, String text) throws IOException {
        if (output == null) throw new IOException("无法写入文本文件");
        Login login = parse(text);
        output.write(encode(login.cookies, login.refreshToken).getBytes("UTF-8"));
        output.flush();
    }

    private static IOException invalid() {
        return new IOException("登录信息格式无效，请选择哔哩终端导出的文本文件");
    }
}
