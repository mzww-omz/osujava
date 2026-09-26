package dev.osujava.ruleset.osu.render;

import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.HitObjectVisual;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.gameplay.SliderVisual;
import dev.osujava.gameplay.SpinnerVisual;
import java.util.ArrayList;
import java.util.List;

/** Back-to-front commands. No GPU state, gameplay mutations, or per-frame sorting. */
public final class OsuRenderPlan {
    public enum Layer { SPINNER_PROXY, JUDGEMENT_BELOW, HIT_OBJECT, JUDGEMENT_ABOVE, APPROACH_PROXY, HUD }
    public enum Piece {
        SPINNER, JUDGEMENT_BELOW, CIRCLE_BASE, NUMBER, CIRCLE_OVERLAY,
        SLIDER_BODY, TAIL_BASE, TAIL_OVERLAY, TICKS, REPEATS,
        HEAD_BASE, REVERSE_ARROWS, HEAD_NUMBER, HEAD_OVERLAY, BALL_AND_FOLLOW,
        JUDGEMENT_ABOVE, APPROACH, HUD
    }
    public record Command(Layer layer, Piece piece, HitObjectVisual object) { }

    public static List<Command> create(GameplayState state, boolean overlayAboveNumber) {
        List<Command> commands = new ArrayList<>();
        List<HitObjectVisual> order = state.drawOrder();
        // Playfield proxy containers use normal ChildID order, unlike HitObjectContainer.
        for (int i = order.size() - 1; i >= 0; i--)
            if (order.get(i) instanceof SpinnerVisual spinner)
                commands.add(new Command(Layer.SPINNER_PROXY, Piece.SPINNER, spinner));
        commands.add(new Command(Layer.JUDGEMENT_BELOW, Piece.JUDGEMENT_BELOW, null));
        for (HitObjectVisual object : order) {
            if (object instanceof HitCircleVisual) {
                add(commands, object, Piece.CIRCLE_BASE);
                addCircleForeground(commands, object, overlayAboveNumber, Piece.NUMBER, Piece.CIRCLE_OVERLAY);
            } else if (object instanceof SliderVisual) {
                // DrawableSlider: body, tail proxy, ticks, repeats, head, OverlayElementContainer, ball.
                add(commands, object, Piece.SLIDER_BODY, Piece.TAIL_BASE, Piece.TAIL_OVERLAY,
                        Piece.TICKS, Piece.REPEATS, Piece.HEAD_BASE, Piece.REVERSE_ARROWS);
                // Legacy head OverlayLayer is proxied at float.MinValue above arrows within this slider.
                addCircleForeground(commands, object, overlayAboveNumber, Piece.HEAD_NUMBER, Piece.HEAD_OVERLAY);
                add(commands, object, Piece.BALL_AND_FOLLOW);
            }
        }
        commands.add(new Command(Layer.JUDGEMENT_ABOVE, Piece.JUDGEMENT_ABOVE, null));
        for (int i = order.size() - 1; i >= 0; i--)
            if (!(order.get(i) instanceof SpinnerVisual))
                commands.add(new Command(Layer.APPROACH_PROXY, Piece.APPROACH, order.get(i)));
        commands.add(new Command(Layer.HUD, Piece.HUD, null));
        return List.copyOf(commands);
    }

    private static void addCircleForeground(List<Command> commands, HitObjectVisual object,
            boolean overlayAboveNumber, Piece number, Piece overlay) {
        add(commands, object, overlayAboveNumber ? number : overlay, overlayAboveNumber ? overlay : number);
    }

    private static void add(List<Command> commands, HitObjectVisual object, Piece... pieces) {
        for (Piece piece : pieces) commands.add(new Command(Layer.HIT_OBJECT, piece, object));
    }

    private OsuRenderPlan() { }
}
