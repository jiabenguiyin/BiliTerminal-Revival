package com.RobinNotBad.BiliClient.player;

/** Recognizes one deliberate vertical flick after the normal long-press timer has fired. */
public final class LongPressSwitchGesture {
    public static final int NONE = 0, NEXT = 1, PREVIOUS = -1;
    private float x, y, distance, slop, velocity;
    private long movementStart = -1;
    private boolean active;
    public void arm(float x, float y, float height, float density, float touchSlop) {
        this.x = x; this.y = y;
        distance = Math.min(height * .4f, Math.max(56f * density, height * .25f));
        slop = touchSlop; velocity = Math.max(160f, 300f * density);
        movementStart = -1; active = true;
    }
    public int update(float currentX, float currentY, long time) {
        if (!active) return NONE;
        float dx = Math.abs(currentX - x), dy = currentY - y, length = Math.abs(dy);
        if (dx <= slop && length <= slop) return NONE;
        if (movementStart < 0) movementStart = time;
        if (time - movementStart > 350 || dx > Math.max(slop * 2, length / 1.8f)) {
            active = false; return NONE;
        }
        if (length < distance || length * 1000f / Math.max(16, time - movementStart) < velocity) return NONE;
        active = false;
        return dy > 0 ? NEXT : PREVIOUS;
    }
    public void cancel() { active = false; }
}
