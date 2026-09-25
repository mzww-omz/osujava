package dev.osujava.gameplay;

/** Beatmap-clock animation for a Slider follow circle. Tracking itself is supplied by gameplay. */
public final class FollowCircleAnimation {
    public static final double FOLLOW_SCALE = 2.4;

    private boolean initialized;
    private boolean tracking;
    private boolean ended;
    private long transitionStartMs;
    private double transitionDurationMs;
    private double startScale = 1;
    private double targetScale = 1;
    private double startAlpha;
    private double targetAlpha;

    public void update(boolean trackingNow, double currentTimeMs, double endTimeMs) {
        if (!initialized) {
            initialized = true;
            tracking = trackingNow;
            transitionStartMs = (long) currentTimeMs;
            startScale = targetScale = trackingNow ? FOLLOW_SCALE : 1;
            startAlpha = targetAlpha = trackingNow ? 1 : 0;
        }

        if (!ended && currentTimeMs >= endTimeMs) {
            double currentScale = scaleAt(currentTimeMs);
            double currentAlpha = alphaAt(currentTimeMs);
            beginTransition(currentTimeMs, currentScale, 1, currentAlpha, 0, 300);
            tracking = false;
            ended = true;
        } else if (!ended && trackingNow != tracking) {
            double currentScale = scaleAt(currentTimeMs);
            double currentAlpha = alphaAt(currentTimeMs);
            if (trackingNow) {
                beginTransition(currentTimeMs, currentScale, FOLLOW_SCALE, currentAlpha, 1, 300);
            } else {
                beginTransition(currentTimeMs, currentScale, FOLLOW_SCALE * 1.2, currentAlpha, 0, 150);
            }
            tracking = trackingNow;
        }
    }

    public double alphaAt(double currentTimeMs) {
        return interpolate(startAlpha, targetAlpha, currentTimeMs);
    }

    public double scaleAt(double currentTimeMs) {
        return interpolate(startScale, targetScale, currentTimeMs);
    }

    private void beginTransition(double timeMs, double fromScale, double toScale,
                                 double fromAlpha, double toAlpha, double durationMs) {
        transitionStartMs = (long) timeMs;
        transitionDurationMs = durationMs;
        startScale = fromScale;
        targetScale = toScale;
        startAlpha = fromAlpha;
        targetAlpha = toAlpha;
    }

    private double interpolate(double from, double to, double currentTimeMs) {
        double progress = GameplayVisualTiming.easeOutQuint(
                GameplayVisualTiming.progress(currentTimeMs, transitionStartMs, transitionDurationMs));
        return from + (to - from) * progress;
    }
}
