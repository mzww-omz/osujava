package dev.osujava.ui;

/** Carousel viewport in stable's 480-high reference units and milliseconds; no input or rendering. */
final class SongSelectScroll {
    static final double IDLE_DECAY = .9959999918937683;
    static final double SELECT_DECAY = .99, TRACK_DECAY = .992, WHEEL_DECAY = .994;
    private double position, limit, velocity, decay = IDLE_DECAY;
    private boolean dragging;
    private double stationaryMs;

    boolean dragging() { return dragging; }
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
        // 06003245/3246 receive one direction, with no wheel magnitude argument.
        velocity += Math.signum(amount) * .4 * (1 + Math.min(Math.abs(velocity) / 2, 5));
        decay = WHEEL_DECAY;
    }
    void beginDrag() { dragging = true; stationaryMs = 0; }
    void drag(double distance, double milliseconds) {
        if (!dragging || !Double.isFinite(distance)) return;
        // Geometry-only updates may move the pointer, but cannot manufacture a velocity sample.
        position = clamp(position + distance);
        if (!Double.isFinite(milliseconds) || milliseconds <= 0) return;
        stationaryMs += milliseconds;
        double measured = distance / stationaryMs;
        if (measured == 0) return;
        double base = Math.signum(measured) == -Math.signum(velocity)
                || Math.abs(measured) > Math.abs(velocity) ? .9 : .95;
        double weight = Math.pow(base, stationaryMs);
        velocity = velocity * weight + (1 - weight) * measured;
        decay = velocity == 0 ? .5 : Math.max(.5, IDLE_DECAY - .002 / Math.abs(velocity));
        stationaryMs = 0;
    }
    void releaseDrag() {
        if (!dragging) return;
        velocity *= Math.pow(.95, Math.max(0, stationaryMs - 66));
        stationaryMs = 0; dragging = false;
    }
    void cancelDrag() {
        if (!dragging) return;
        dragging = false; stationaryMs = 0; jump(position);
    }
    void advance(double milliseconds) {
        if (dragging || !Double.isFinite(milliseconds) || milliseconds <= 0) return;
        double log = Math.log(decay);
        double travel = velocity * Math.expm1(log * milliseconds) / log;
        velocity *= Math.exp(log * milliseconds);
        // 06003254 stops after this frame's integration, retaining its sub-threshold residual distance.
        if (Math.abs(velocity) < .01) { velocity = 0; decay = IDLE_DECAY; }
        position = clamp(position + travel);
    }
    private double clamp(double value) { return Math.max(0, Math.min(limit, value)); }
}
