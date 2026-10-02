package dev.osujava.score;

import dev.osujava.beatmap.BeatmapContentKey;
import dev.osujava.gameplay.GameplayRunMode;
import java.util.List;
import java.util.TreeSet;

/** Frozen at gameplay creation. A null context on old scores means these facts were uncollected. */
public record PlayContext(BeatmapContentKey content, int mode, String rulesetId, String rulesetVersion,
                          String scoringVersion, List<String> mods, LocalPlayer player, GameplayRunMode runMode) {
    public PlayContext {
        if (mode < 0 || content != null && content.mode() != mode || runMode == null || mods == null
                || !identifier(rulesetId) || !identifier(rulesetVersion) || !identifier(scoringVersion))
            throw new IllegalArgumentException("Invalid play context");
        if (mods.size() > 32 || mods.stream().anyMatch(m -> m == null || !m.matches("[A-Z0-9]{1,8}")))
            throw new IllegalArgumentException("Invalid recorded Mods");
        mods = List.copyOf(new TreeSet<>(mods));
    }
    private static boolean identifier(String value) { return value != null && value.matches("[a-z0-9][a-z0-9._-]{0,63}"); }
    public boolean matches(ScoreDetails details) {
        return scoringVersion.equals(details.scoringVersion())
                && (content == null ? "" : content.sha256()).equals(details.beatmapSha256());
    }
}
