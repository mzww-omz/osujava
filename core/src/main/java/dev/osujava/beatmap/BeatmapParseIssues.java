package dev.osujava.beatmap;

/** Source damage retained by tolerant import; optional/absent fields are not damage. */
public record BeatmapParseIssues(int settingCount, int timingPointCount, int correctedObjectCount) {
    public static final BeatmapParseIssues NONE = new BeatmapParseIssues(0, 0, 0);
    public BeatmapParseIssues {
        if (settingCount < 0 || timingPointCount < 0 || correctedObjectCount < 0)
            throw new IllegalArgumentException("Parse issue counts cannot be negative");
    }
    public boolean any() { return settingCount > 0 || timingPointCount > 0 || correctedObjectCount > 0; }
    public String description() {
        return settingCount + " invalid setting(s), " + timingPointCount + " invalid TimingPoint line(s), "
                + correctedObjectCount + " corrected HitObject(s)";
    }
}
