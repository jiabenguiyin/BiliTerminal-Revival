package com.RobinNotBad.BiliClient.util;

/** Per-page decode limits, independent of screen DPI and source compression size. */
public final class ImageDisplayPolicy {
    private ImageDisplayPolicy() { }
    public static int tileSide(long heapBytes) { return heapBytes <= 128L * 1024 * 1024 ? 512 : 1024; }
    public static int previewSide(long heapBytes) { return tileSide(heapBytes) * 2; }
    public static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= 200000 && height <= 200000
                && (long) width * height <= 500000000L;
    }
    public static boolean canAnimate(int width, int height, long heapBytes) {
        int side = previewSide(heapBytes);
        // GIF decoding retains source-sized scratch buffers, even when frames are sampled.
        return validDimensions(width, height) && width <= side && height <= side
                && (long) width * height * 4 <= (heapBytes <= 128L * 1024 * 1024 ? 2L : 4L) * 1024 * 1024;
    }
    public static boolean safePreview(int width, int height, long bytes, long heapBytes) {
        int side = previewSide(heapBytes);
        return validDimensions(width, height) && width <= side && height <= side
                && bytes > 0 && bytes <= (long) side * side * 4;
    }
}
