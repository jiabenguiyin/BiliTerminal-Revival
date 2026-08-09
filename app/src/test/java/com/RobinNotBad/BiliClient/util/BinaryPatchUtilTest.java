package com.RobinNotBad.BiliClient.util;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

public class BinaryPatchUtilTest {
    private static final String OLD_BASE64 =
            "QmlsaVRlcm1pbmFsIG9sZCBwYXlsb2FkIDAxMjM0NTY3ODkgYWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoKc2Vjb25kIGxpbmUgc3RheXMK";
    private static final String NEW_BASE64 =
            "QmlsaVRlcm1pbmFsIG5ldyBwYXlsb2FkIDAxMjM0NTY3ODkgYWJjWFlaZ2hpamtsbW5vcHFyc3R1dnd4eXoKc2Vjb25kIGxpbmUgc3RheXMKdGhpcmQgbGluZSBhZGRlZAo=";
    private static final String PATCH_BASE64 =
            "QlNESUZGNDAyAAAAAAAAADQAAAAAAAAAYgAAAAAAAABCWmg5MUFZJlNZMBA5dwAACOJAeAAgACAAQAAgACGahkIYA6a0oMt8XckU4UJAwEDl3EJaaDkxQVkmU1kE6o50AAAAcADAIAgACAAEIKAAMQwAlD1Gi6lHWYMni7kinChIAnVHOgBCWmg5MUFZJlNZPb+NAwAACFGAABBAACZlFAAgACIaAZCAaaaJoTBuiEjyZeLuSKcKEge38aBg";

    @Test
    public void appliesStandardBsdiff40Patch() throws Exception {
        File dir = Files.createTempDirectory("biliterminal-bspatch").toFile();
        File oldFile = new File(dir, "old.bin");
        File patchFile = new File(dir, "update.patch");
        File outputFile = new File(dir, "new.bin");
        Files.write(oldFile.toPath(), Base64.getDecoder().decode(OLD_BASE64));
        Files.write(patchFile.toPath(), Base64.getDecoder().decode(PATCH_BASE64));

        BinaryPatchUtil.applyBsdiff(oldFile, patchFile, outputFile);

        assertArrayEquals(
                Base64.getDecoder().decode(NEW_BASE64),
                Files.readAllBytes(outputFile.toPath())
        );
    }
}
