package com.RobinNotBad.BiliClient.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.WireFormat;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CommentTranslationApiTest {
    @Test
    public void requestUsesOfficialFieldNumbers() throws Exception {
        CodedInputStream input = CodedInputStream.newInstance(
                CommentTranslationApi.encodeRequest(987654321L, 1, 112233L));

        assertEquals((1 << 3) | WireFormat.WIRETYPE_VARINT, input.readTag());
        assertEquals(1L, input.readInt64());
        assertEquals((2 << 3) | WireFormat.WIRETYPE_VARINT, input.readTag());
        assertEquals(987654321L, input.readInt64());
        assertEquals((3 << 3) | WireFormat.WIRETYPE_VARINT, input.readTag());
        assertEquals(112233L, input.readInt64());
        assertEquals(0, input.readTag());
    }

    @Test
    public void extractsTranslationForRequestedReplyId() throws Exception {
        byte[] response = responseFrame(112233L, "Translated comment");
        assertEquals("Translated comment", CommentTranslationApi.parseResponse(response, 112233L));
    }

    @Test
    public void ignoresOtherReplyIds() throws Exception {
        try {
            CommentTranslationApi.parseResponse(responseFrame(112233L, "Other comment"), 998877L);
            fail("Expected missing translation");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("没有返回"));
        }
    }

    @Test
    public void rejectsCompressedAndTruncatedFrames() throws Exception {
        byte[] compressed = responseFrame(112233L, "Text");
        compressed[0] = 1;
        try {
            CommentTranslationApi.parseResponse(compressed, 112233L);
            fail("Expected unsupported compression");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("压缩"));
        }

        try {
            CommentTranslationApi.parseResponse(new byte[]{0, 0, 0, 0}, 112233L);
            fail("Expected invalid frame");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("不完整"));
        }
    }

    private static byte[] responseFrame(long rpid, String translation) throws Exception {
        ByteArrayOutputStream translatedContent = new ByteArrayOutputStream();
        CodedOutputStream contentOutput = CodedOutputStream.newInstance(translatedContent);
        contentOutput.writeString(1, translation);
        contentOutput.flush();

        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        CodedOutputStream replyOutput = CodedOutputStream.newInstance(reply);
        replyOutput.writeByteArray(17, translatedContent.toByteArray());
        replyOutput.flush();

        ByteArrayOutputStream entry = new ByteArrayOutputStream();
        CodedOutputStream entryOutput = CodedOutputStream.newInstance(entry);
        entryOutput.writeInt64(1, rpid);
        entryOutput.writeByteArray(2, reply.toByteArray());
        entryOutput.flush();

        ByteArrayOutputStream response = new ByteArrayOutputStream();
        CodedOutputStream responseOutput = CodedOutputStream.newInstance(response);
        responseOutput.writeByteArray(1, entry.toByteArray());
        responseOutput.flush();
        return CommentTranslationApi.frame(response.toByteArray());
    }
}
