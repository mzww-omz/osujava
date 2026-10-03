package dev.osujava.difficulty;

import java.util.OptionalDouble;

/** A missing/failed calculation is never a numeric zero. Immutable across the worker boundary. */
public record DifficultyResult(Status status, Double stars, Double aim, Double speed, String reason) {
    public enum Status { PENDING, SUCCESS, UNSUPPORTED, FAILED }
    public DifficultyResult {
        if (status == null || reason == null || reason.length() > 256) throw new IllegalArgumentException("Invalid result");
        if (status == Status.SUCCESS) {
            if (!valid(stars) || !valid(aim) || !valid(speed) || !reason.isEmpty()) throw new IllegalArgumentException("Invalid rating");
        } else if (stars != null || aim != null || speed != null) throw new IllegalArgumentException("Uncalculated rating");
    }
    private static boolean valid(Double value) { return value != null && Double.isFinite(value) && value >= 0; }
    public OptionalDouble rating() { return stars == null ? OptionalDouble.empty() : OptionalDouble.of(stars); }
    public static DifficultyResult pending() { return new DifficultyResult(Status.PENDING,null,null,null,""); }
    public static DifficultyResult unsupported(String reason) { return new DifficultyResult(Status.UNSUPPORTED,null,null,null,reason); }
    public static DifficultyResult failed(String reason) { return new DifficultyResult(Status.FAILED,null,null,null,reason); }
}
