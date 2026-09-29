package dev.osujava.ui;

/** Carousel viewport in stable's 480-high reference units and milliseconds; no input or rendering. */
final class SongSelectScroll {
    static final double IDLE_DECAY = .9959999918937683;
    static final double SELECT_DECAY = .99, TRACK_DECAY = .992, WHEEL_DECAY = .994;
    private double position, limit, velocity, decay = IDLE_DECAY;

    double position() { return position; }
    double velocity() { return velocity; }
    double decay() { return decay; }
    double remaining() { return -velocity / Math.log(decay); }
    double destination() { return clamp(position + remaining()); }

    void range(double value) {
        limit = Double.isFinite(value) ? Math.max(0, value) : 0;
        position = clamp(position);
        if (limit == 0) jump(0);
    }
    void jump(double target) {
        if (!Double.isFinite(target)) return;
        position = clamp(target); velocity = 0; decay = IDLE_DECAY;
    }
    void seek(double target, double coefficient) {
        if (!Double.isFinite(target) || !(coefficient > 0 && coefficient < 1)) return;
        decay = coefficient;
        velocity = -(clamp(target) - position) * Math.log(decay);
    }
    void wheel(float amount) {
        if (!Float.isFinite(amount) || amount == 0 || limit == 0) return;
        // Native callbacks represent one notch. Split batched notches; interpolate a trackpad remainder.
        // The finite event cap is the existing Java input guard, not a stable velocity limit.
        double left = Math.min(10000, Math.abs((double) amount));
        while (left > 0) {
            double part = Math.min(1, left);
            velocity += Math.signum(amount) * .4 * (1 + Math.min(Math.abs(velocity) / 2, 5)) * part;
            left -= part;
        }
        decay = WHEEL_DECAY;
    }
    void advance(double milliseconds) {
        if (!Double.isFinite(milliseconds) || milliseconds <= 0) return;
        double log = Math.log(decay);
        double travel = velocity * Math.expm1(log * milliseconds) / log;
        velocity *= Math.exp(log * milliseconds);
        // 06003254 stops after this frame's integration, retaining its sub-threshold residual distance.
        if (Math.abs(velocity) < .01) { velocity = 0; decay = IDLE_DECAY; }
        position = clamp(position + travel);
    }
    private double clamp(double value) { return Math.max(0, Math.min(limit, value)); }
}
