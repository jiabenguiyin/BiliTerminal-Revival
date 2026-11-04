package com.RobinNotBad.BiliClient.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImageDisplayPolicyTest {
    @Test public void sourceBoundsAreCheckedWithoutIntegerOverflow() {
        assertTrue(ImageDisplayPolicy.validDimensions(4096,7294));
        assertTrue(ImageDisplayPolicy.validDimensions(2048,30000));
        assertFalse(ImageDisplayPolicy.validDimensions(0,100));
        assertFalse(ImageDisplayPolicy.validDimensions(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertFalse(ImageDisplayPolicy.validDimensions(200000,200000));
    }
    @Test public void ordinaryAnimationsRemainEnabledButLargeFramesCannotAllocateUnboundedScratchBuffers() {
        assertTrue(ImageDisplayPolicy.canAnimate(320,240,64L*1024*1024));
        assertFalse(ImageDisplayPolicy.canAnimate(4096,7294,512L*1024*1024));
        assertFalse(ImageDisplayPolicy.canAnimate(1,200000,64L*1024*1024));
        assertEquals(512,ImageDisplayPolicy.tileSide(64L*1024*1024));
        assertEquals(1024,ImageDisplayPolicy.tileSide(512L*1024*1024));
    }
    @Test public void decodedPreviewHasBothTextureAndMemoryBounds() {
        long heap=64L*1024*1024;
        assertTrue(ImageDisplayPolicy.safePreview(320,240,320*240*4,heap));
        assertFalse(ImageDisplayPolicy.safePreview(4096,7294,119504896,heap));
        assertFalse(ImageDisplayPolicy.safePreview(320,240,119504896,heap));
        assertFalse(ImageDisplayPolicy.safePreview(1,4096,4096*4,heap));
    }
}
