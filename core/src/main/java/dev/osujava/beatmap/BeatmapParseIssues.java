package dev.osujava.beatmap;

/** Source diagnostics retained by tolerant import; optional fields and ordering alone are not damage. */
public record BeatmapParseIssues(int settingCount, int timingPointCount, int correctedObjectCount, boolean timingOrderChanged) {
    public static final BeatmapParseIssues NONE = new BeatmapParseIssues(0, 0, 0, false);
    public BeatmapParseIssues(int settingCount, int timingPointCount, int correctedObjectCount) {
        this(settingCount, timingPointCount, correctedObjectCount, false);
    }
    public BeatmapParseIssues {
        if (settingCount < 0 || timingPointCount < 0 || correctedObjectCount < 0)
            throw new IllegalArgumentException("Parse issue counts cannot be negative");
    }
    /** Numeric/structural damage; source reordering is a separate compatibility diagnostic. */
    public boolean any() { return settingCount > 0 || timingPointCount > 0 || correctedObjectCount > 0; }
    public String description() {
        return settingCount + " invalid setting(s), " + timingPointCount + " invalid TimingPoint line(s), "
                + correctedObjectCount + " corrected HitObject(s)";
    }
}
