package com.RobinNotBad.BiliClient.util;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.Assert.*;

public class LoginTextFileTest {
    private static final String COOKIE = "DedeUserID=42; SESSDATA=fixture==; bili_jct=fixture-csrf";
    @Test public void exportedTextRoundTripsWithoutLosingEscapesOrToken() throws Exception {
        String text = LoginTextFile.encode(COOKIE, "token\\\"中文");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        LoginTextFile.write(output, text);
        LoginTextFile.Login parsed = LoginTextFile.parse(LoginTextFile.read(new ByteArrayInputStream(output.toByteArray())));
        assertEquals(COOKIE, parsed.cookies);
        assertEquals("token\\\"中文", parsed.refreshToken);
    }
    @Test public void readsNotepadBomEncodings() throws Exception {
        String text = LoginTextFile.encode(COOKIE, "token");
        for (String encoding : new String[]{"UTF-8", "UTF-16LE", "UTF-16BE"}) {
            byte[] bom = encoding.equals("UTF-8") ? new byte[]{(byte)239,(byte)187,(byte)191}
                    : encoding.equals("UTF-16LE") ? new byte[]{(byte)255,(byte)254} : new byte[]{(byte)254,(byte)255};
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(bom); out.write(text.getBytes(encoding));
            assertEquals(COOKIE, LoginTextFile.parse(LoginTextFile.read(new ByteArrayInputStream(out.toByteArray()))).cookies);
        }
    }
    @Test public void missingRefreshTokenIsSupportedButMissingSessionIsRejected() throws Exception {
        assertEquals("", LoginTextFile.parse("{\"cookies\":\"" + COOKIE + "\"}").refreshToken);
        for (String text : new String[]{"", "[]", "broken", "{}", "{\"cookies\":42}",
                "{\"cookies\":\"DedeUserID=42; SESSDATA=fixture\"}",
                "{\"cookies\":\"" + COOKIE + "\",\"refresh_token\":{}}"}) {
            try { LoginTextFile.parse(text); fail("Should reject malformed login"); }
            catch (IOException expected) { assertFalse(expected.getMessage().contains("fixture")); }
        }
    }
    @Test public void oversizedAndMalformedEncodingAreRejected() throws Exception {
        try { LoginTextFile.read(new ByteArrayInputStream(new byte[LoginTextFile.MAX_BYTES + 1])); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("64KB")); }
        try { LoginTextFile.read(new ByteArrayInputStream(new byte[]{(byte)0xC3, (byte)0x28})); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("UTF")); }
    }
    @Test public void rawCookieHeaderPreservesTokenEscapesAndNormalizesAuthNames()throws Exception {
        LoginTextFile.Login value=LoginTextFile.parse("Cookie: dedeuserid=42; sessdata=a%2Cb+z==; BILI_JCT=csrf");
        Cookies cookies=new Cookies(value.cookies);
        assertEquals("42",cookies.getOrDefault("DedeUserID",""));
        assertEquals("a%2Cb+z==",cookies.get("SESSDATA"));assertTrue(value.cookies.contains("SESSDATA="));
        assertEquals("csrf",cookies.get("bili_jct"));
    }
    @Test public void embeddedHeaderNewlinesAreRejected()throws Exception {
        try{LoginTextFile.parse("SESSDATA=x; DedeUserID=42; bili_jct=csrf\r\nInjected: value");fail();}
        catch(IOException expected){assertFalse(expected.getMessage().contains("Injected"));}
    }
}
