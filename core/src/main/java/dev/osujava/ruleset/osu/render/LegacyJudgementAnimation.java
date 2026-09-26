package dev.osujava.ruleset.osu.render;

import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.JudgementVisual;
import java.util.List;
import java.util.Random;

/** Absolute gameplay-time sampling of lazer's legacy judgement pieces (no gameplay mutation). */
public final class LegacyJudgementAnimation {
    public static final double FRAME_MS = 1000d / 60;
    public static final int PARTICLE_COUNT = 150;
    public static final int MAX_LIFETIME_MS = 1500; // Restart at -100 + 1600.
    public enum Result {
        GREAT("hit300", "particle300"), OK("hit100", "particle100"), MEH("hit50", "particle50"),
        MISS("hit0", null), SLIDER_END_MISS("sliderendmiss", null), SLIDER_TAIL_HIT("sliderpoint10", null);
        public final String image, particle;
        Result(String image, String particle) { this.image = image; this.particle = particle; }
        public static Result from(Judgement result) {
            return switch (result) { case HIT300 -> GREAT; case HIT100 -> OK; case HIT50 -> MEH; case MISS -> MISS; };
        }
        public static Result from(JudgementVisual visual) {
            if (visual.kind() == JudgementVisual.Kind.SLIDER_TAIL)
                return visual.judgement() == Judgement.MISS ? SLIDER_END_MISS : SLIDER_TAIL_HIT;
            return from(visual.judgement());
        }
    }
    public enum Style { NONE, OLD, NEW, SLIDER_POINT }
    public record Transform(double alpha, double scale, double y, double rotation) { }
    public record Particle(double distance, double duration, double direction) {
        public double progress(double age) { return clamp((age + 100) / duration); }
        public double x(double age) { return distance * progress(age) * Math.sin(direction) * 140; }
        public double y(double age) { return distance * progress(age) * Math.cos(direction) * 140; }
        public double alpha(double age) { return (1 - progress(age)) * (1 - clamp((age + 100) / 1600)); }
    }
    public static int frame(double age, int count) {
        if (count < 1) throw new IllegalArgumentException("An animation needs a frame");
        return (int) Math.min(count - 1, Math.floor(Math.max(0, age) / FRAME_MS));
    }
    public static double alpha(double age) {
        return age < 0 ? 0 : clamp(age / 120) * (1 - clamp((age - 500) / 600));
    }
    public static double hitScale(double age, double finalScale) {
        if (age < 96) return lerp(.6, 1.1, age / 96);
        if (age < 120) return 1.1;
        if (age < 144) return lerp(1.1, .9, (age - 120) / 24);
        return lerp(.95, finalScale, (age - 144) / 24);
    }
    public static Transform old(Result result, double age, int frames, double version, double rotation) {
        double alpha = alpha(age);
        if (frames > 1) return new Transform(alpha, 1, 0, 0);
        if (result == Result.SLIDER_END_MISS)
            return new Transform(age < 0 ? 0 : clamp(age / 120) * (1 - clamp((age - 250) / 600)),
                    lerp(1.2, 1, square(age / 100)), 0, 0);
        if (result == Result.MISS) {
            double angle = age < 120 ? lerp(0, rotation, age / 120)
                    : lerp(rotation, rotation * 2, square((age - 120) / 980));
            return new Transform(alpha, lerp(1.6, 1, square(age / 100)),
                    version > 1 ? -5 + 80 * square(age / 1100) : 0, angle);
        }
        return new Transform(alpha, hitScale(age, 1), 0, 0);
    }
    public static Transform main(double age, int frames) {
        return new Transform(alpha(age), frames > 1 ? 1 : lerp(.9, 1.05, age / 1100), 0, 0);
    }
    public static Transform temporary(double age) {
        double p = clamp((age + 16) / 56);
        double opacity = age < 40 ? .5 * p * (2 - p) : .5 * (1 - clamp((age - 40) / 300));
        return new Transform(alpha(age) * opacity, hitScale(age, 1.05), 0, 0);
    }
    public static Transform sliderPoint(double age) {
        double p = clamp(age / 300);
        return new Transform(age < 0 ? 0 : 1 - clamp((age - 300) / 60), 1, -10 * p * (2 - p), 0);
    }
    /** Same ranges/distributions as RNG in lazer; seeded once per immutable visual event. */
    public static double missRotation(JudgementVisual visual) {
        return new Random(visual.effectSeed()).nextFloat() * 17.2 - 8.6;
    }
    public static List<Particle> particles(JudgementVisual visual) {
        Random random = new Random(visual.effectSeed());
        var parts = new java.util.ArrayList<Particle>(PARTICLE_COUNT);
        for (int i = 0; i < PARTICLE_COUNT; i++) parts.add(new Particle(random.nextFloat() * .5,
                1600d / 3 + random.nextDouble() * (1600 - 1600d / 3), random.nextFloat() * Math.PI * 2));
        return List.copyOf(parts);
    }
    /** DrawableOsuJudgement.Scale = OsuHitObject.Scale; OBJECT_RADIUS = 64. */
    public static double baseScale(double radius) { return radius / 64; }
    private static double clamp(double p) { return Math.max(0, Math.min(1, p)); }
    private static double square(double p) { p = clamp(p); return p * p; }
    private static double lerp(double a, double b, double p) { return a + (b - a) * clamp(p); }
    private LegacyJudgementAnimation() { }
}
