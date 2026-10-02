package dev.osujava.support;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit wall time for persistence/calendar tests, independent of gameplay's monotonic clock. */
public final class MutableWallClock extends Clock {
    private final AtomicReference<Instant> time;
    private final ZoneId zone;
    public MutableWallClock(String instant, ZoneId zone) { this(new AtomicReference<>(Instant.parse(instant)),zone); }
    private MutableWallClock(AtomicReference<Instant> time, ZoneId zone) { this.time=time; this.zone=zone; }
    public void set(String instant) { time.set(Instant.parse(instant)); }
    @Override public Instant instant() { return time.get(); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId next) { return new MutableWallClock(time,next); }
}
