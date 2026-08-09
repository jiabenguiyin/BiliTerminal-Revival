package com.RobinNotBad.BiliClient.util;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;

public final class BinaryPatchUtil {
    private static final byte[] MAGIC = new byte[]{
            'B', 'S', 'D', 'I', 'F', 'F', '4', '0'
    };
    private static final int HEADER_SIZE = 32;
    private static final int BUFFER_SIZE = 32 * 1024;

    private BinaryPatchUtil() {
    }

    public static void applyBsdiff(File oldFile, File patchFile, File outputFile) throws IOException {
        long controlLength;
        long diffLength;
        long newSize;
        try (RandomAccessFile patchHeader = new RandomAccessFile(patchFile, "r")) {
            byte[] header = new byte[HEADER_SIZE];
            patchHeader.readFully(header);
            for (int i = 0; i < MAGIC.length; i++) {
                if (header[i] != MAGIC[i]) throw new IOException("invalid BSDIFF40 patch");
            }
            controlLength = decodeOffset(header, 8);
            diffLength = decodeOffset(header, 16);
            newSize = decodeOffset(header, 24);
        }
        if (controlLength < 0 || diffLength < 0 || newSize < 0) {
            throw new IOException("invalid patch lengths");
        }
        long extraOffset = HEADER_SIZE + controlLength + diffLength;
        if (extraOffset < HEADER_SIZE || extraOffset > patchFile.length()) {
            throw new IOException("truncated patch");
        }

        if (outputFile.exists() && !outputFile.delete()) {
            throw new IOException("cannot replace old update output");
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create update output directory");
        }

        try (RandomAccessFile oldInput = new RandomAccessFile(oldFile, "r");
             InputStream controlInput = openBzip2Section(patchFile, HEADER_SIZE, controlLength);
             InputStream diffInput = openBzip2Section(patchFile, HEADER_SIZE + controlLength, diffLength);
             InputStream extraInput = openBzip2Section(patchFile, extraOffset, patchFile.length() - extraOffset);
             FileOutputStream output = new FileOutputStream(outputFile)) {
            byte[] controlBuffer = new byte[8];
            byte[] diffBuffer = new byte[BUFFER_SIZE];
            byte[] oldBuffer = new byte[BUFFER_SIZE];
            byte[] extraBuffer = new byte[BUFFER_SIZE];
            long oldPosition = 0;
            long newPosition = 0;

            while (newPosition < newSize) {
                long diffBytes = readOffset(controlInput, controlBuffer);
                long extraBytes = readOffset(controlInput, controlBuffer);
                long oldSeek = readOffset(controlInput, controlBuffer);
                if (diffBytes < 0 || extraBytes < 0 || newPosition + diffBytes > newSize) {
                    throw new IOException("corrupt patch control block");
                }

                long processed = 0;
                while (processed < diffBytes) {
                    int count = (int) Math.min(diffBuffer.length, diffBytes - processed);
                    readFully(diffInput, diffBuffer, count);
                    Arrays.fill(oldBuffer, 0, count, (byte) 0);

                    long segmentStart = oldPosition + processed;
                    long validStart = Math.max(0, segmentStart);
                    long validEnd = Math.min(oldInput.length(), segmentStart + count);
                    if (validEnd > validStart) {
                        int targetOffset = (int) (validStart - segmentStart);
                        int validLength = (int) (validEnd - validStart);
                        oldInput.seek(validStart);
                        oldInput.readFully(oldBuffer, targetOffset, validLength);
                    }

                    for (int i = 0; i < count; i++) {
                        diffBuffer[i] = (byte) (diffBuffer[i] + oldBuffer[i]);
                    }
                    output.write(diffBuffer, 0, count);
                    processed += count;
                }
                newPosition += diffBytes;
                oldPosition += diffBytes;

                if (newPosition + extraBytes > newSize) {
                    throw new IOException("corrupt patch extra block");
                }
                copyExact(extraInput, output, extraBuffer, extraBytes);
                newPosition += extraBytes;
                oldPosition += oldSeek;
            }
            output.getFD().sync();
        } catch (Throwable error) {
            outputFile.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("cannot apply binary patch", error);
        }
    }

    private static InputStream openBzip2Section(File patchFile, long offset, long length) throws IOException {
        FileInputStream input = new FileInputStream(patchFile);
        try {
            skipFully(input, offset);
            return new BZip2CompressorInputStream(new LimitedInputStream(input, length));
        } catch (Throwable error) {
            input.close();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("cannot open patch section", error);
        }
    }

    private static long readOffset(InputStream input, byte[] buffer) throws IOException {
        readFully(input, buffer, buffer.length);
        return decodeOffset(buffer, 0);
    }

    private static long decodeOffset(byte[] buffer, int offset) {
        long value = buffer[offset + 7] & 0x7fL;
        for (int i = 6; i >= 0; i--) {
            value = value * 256L + (buffer[offset + i] & 0xffL);
        }
        return (buffer[offset + 7] & 0x80) == 0 ? value : -value;
    }

    private static void readFully(InputStream input, byte[] buffer, int length) throws IOException {
        int position = 0;
        while (position < length) {
            int read = input.read(buffer, position, length - position);
            if (read < 0) throw new IOException("truncated patch stream");
            position += read;
        }
    }

    private static void copyExact(InputStream input, FileOutputStream output, byte[] buffer, long length)
            throws IOException {
        long remaining = length;
        while (remaining > 0) {
            int count = (int) Math.min(buffer.length, remaining);
            readFully(input, buffer, count);
            output.write(buffer, 0, count);
            remaining -= count;
        }
    }

    private static void skipFully(InputStream input, long length) throws IOException {
        long remaining = length;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) throw new IOException("truncated patch file");
                skipped = 1;
            }
            remaining -= skipped;
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream input, long remaining) {
            super(input);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) return -1;
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) remaining -= read;
            return read;
        }
    }
}
