package com.RobinNotBad.BiliClient.player;

/** A rounded corner radius describes a curve, not a rectangular window inset. */
public final class PlayerSafeAreaPolicy {
    private PlayerSafeAreaPolicy() { }
    public static int cornerInset(int radius,int width,int height,boolean round,int decorativeLimitPx) {
        int edge=Math.min(width,height);
        // Round watch panels (and OEMs which omit the round flag) report a radius
        // near half the screen. Reserving it on both sides leaves zero content.
        if(round||radius<=0||edge<=0||radius>=edge/3)return 0;
        return Math.min(radius,Math.min(Math.max(0,decorativeLimitPx),edge/20));
    }
}
