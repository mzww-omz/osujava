package dev.osujava.ui;

import dev.osujava.score.PlayContext;
import dev.osujava.score.ScoreDetails;

/** Formats collected provenance once; unavailable legacy facts remain explicitly unknown. */
final class ScoreContextPresentation {
    private ScoreContextPresentation() { }
    static String of(PlayContext context, ScoreDetails details) {
        String source = ScoreDetails.SCORE_V1.equals(details.scoringVersion()) ? "ScoreV1" : "Legacy scoring";
        if (context == null) return "Player/mods unknown · " + source;
        return (context.mods().isEmpty() ? "NM" : String.join(" ",context.mods())) + " · " + source + " · "
                + (context.player() == null ? "Player unknown" : context.player().name());
    }
}
