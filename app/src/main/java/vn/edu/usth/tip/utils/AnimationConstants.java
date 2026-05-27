package vn.edu.usth.tip.utils;

public final class AnimationConstants {

    private AnimationConstants() {}

    // Nav-bar tab activation animation
    public static final int   NAV_SLIDE_UP_DURATION_MS    = 180;
    public static final int   NAV_SLIDE_DOWN_DURATION_MS  = 220;
    public static final int   NAV_ICON_SCALE_DURATION_MS  = 160;
    public static final int   NAV_ICON_RESET_DURATION_MS  = 140;
    public static final int   NAV_DEACTIVATE_DURATION_MS  = 120;
    public static final float NAV_OVERSHOOT_TENSION       = 2.5f;
    public static final float NAV_DECELERATE_FACTOR       = 1.5f;
    public static final float NAV_ICON_OVERSHOOT_TENSION  = 4.0f;

    // Bottom bar show/hide animation
    public static final int   BAR_HIDE_DURATION_MS        = 230;
    public static final int   BAR_SHOW_DURATION_MS        = 400;
    public static final float BAR_HIDE_ACCELERATE_FACTOR  = 1.5f;
    public static final float BAR_SHOW_OVERSHOOT_TENSION  = 1.5f;
}
