package vn.edu.usth.tip.utils;

import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;

public final class AnimUtils {

    private AnimUtils() {}

    /**
     * Press-down then spring-back bounce. Action fires after the press-down phase
     * (~80ms) so the sheet/navigation opens while the bounce-back plays — feels
     * responsive without a full animation delay.
     */
    public static void bounceClick(View view, Runnable action) {
        view.animate().cancel();
        view.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .setDuration(80)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (action != null) action.run();
                    view.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(350)
                            .setInterpolator(new OvershootInterpolator(3.5f))
                            .start();
                })
                .start();
    }
}
