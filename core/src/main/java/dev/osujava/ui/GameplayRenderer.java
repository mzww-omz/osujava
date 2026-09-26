package dev.osujava.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.osujava.gameplay.SliderNestedAnimation;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import dev.osujava.OsuJavaGame;
import dev.osujava.beatmap.BeatmapDifficulty;
import dev.osujava.beatmap.BeatmapPoint;
import dev.osujava.beatmap.BeatmapSet;
import dev.osujava.gameplay.FollowCircleAnimation;
import dev.osujava.gameplay.GameplayState;
import dev.osujava.gameplay.GameplayVisualConfig;
import dev.osujava.gameplay.GameplaySkin;
import dev.osujava.gameplay.GameplaySkinComponent;
import dev.osujava.gameplay.GameplayVisualTiming;
import dev.osujava.gameplay.HitCircleVisual;
import dev.osujava.ruleset.osu.render.OsuRenderPlan;
import dev.osujava.gameplay.Judgement;
import dev.osujava.gameplay.ApproachTimeCalculator;
import dev.osujava.gameplay.SliderVisual;
import dev.osujava.gameplay.SpinnerVisual;
import dev.osujava.skin.OsuSkinAssets;
import dev.osujava.skin.LegacySliderBallAnimation;
import dev.osujava.skin.OsuSkinAssets.Image;
import dev.osujava.skin.OsuSkinAssets.SkinTexture;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Draws immutable gameplay snapshots. All object geometry is transformed from osu! playfield space. */
public final class GameplayRenderer {
    private static final int CIRCLE_SEGMENTS = 36;
    private static final int BODY_CAP_SEGMENTS = 14;
    private static final double SLIDER_POST_FADE_MS = 150;
    private static final double SPINNER_POST_FADE_MS = 320;

    private final OsuJavaGame game;
    private final GameplaySkin visuals;
    private final OsuSkinAssets skinAssets;
    private final Map<List<BeatmapPoint>, SliderRenderData> sliderRenderData = new IdentityHashMap<>();
    private final GameplayHudRenderer hud;

    public GameplayRenderer(OsuJavaGame game) {
        this(game, GameplayVisualConfig.defaults());
    }

    public GameplayRenderer(OsuJavaGame game, GameplaySkin visuals) {
        this(game, visuals, null);
    }

    public GameplayRenderer(OsuJavaGame game, GameplaySkin visuals, OsuSkinAssets skinAssets) {
        this.game = game;
        this.visuals = visuals;
        this.skinAssets = skinAssets;
        this.hud = new GameplayHudRenderer(game, visuals, skinAssets);
    }

    public void render(BeatmapSet set, BeatmapDifficulty difficulty, GameplayState state,
                       PlayfieldViewport viewport, Texture background, String notice) {
        render(set, difficulty, state, viewport, background, notice, null);
    }

    public void render(BeatmapSet set, BeatmapDifficulty difficulty, GameplayState state,
                       PlayfieldViewport viewport, Texture background, String notice, BeatmapPoint debugCursor) {
        Color backgroundColor = visuals.component(GameplaySkinComponent.BACKGROUND);
        Gdx.gl.glClearColor(backgroundColor.r, backgroundColor.g, backgroundColor.b, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        SpriteBatch batch = game.batch();
        if (background != null) {
            batch.setColor(Color.WHITE);
            batch.begin();
            batch.draw(background, viewport.left(), viewport.bottom(), viewport.width(), viewport.height());
            batch.end();
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        ShapeRenderer shapes = game.shapes();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        setColor(shapes, visuals.component(GameplaySkinComponent.PLAYFIELD_TINT), 1);
        shapes.rect(viewport.left(), viewport.bottom(), viewport.width(), viewport.height());

        boolean overlayAboveNumber = skinAssets == null || skinAssets.hitCircleOverlayAboveNumber();
        for (var command : OsuRenderPlan.create(state, overlayAboveNumber)) {
            SliderVisual slider = command.object() instanceof SliderVisual v ? v : null;
            switch (command.piece()) {
                case SPINNER -> {
                    drawSpinnerField(shapes, (SpinnerVisual) command.object(), state, viewport);
                    shapeType(shapes, ShapeRenderer.ShapeType.Line);
                    drawSpinnerOverlay(shapes, (SpinnerVisual) command.object(), state, viewport);
                    shapeType(shapes, ShapeRenderer.ShapeType.Filled);
                }
                case JUDGEMENT_BELOW -> {
                    sprites(shapes, () -> {
                        hud.drawJudgementText(batch, state, viewport);
                        hud.drawSpinnerText(batch, state, viewport);
                    });
                }
                case CIRCLE_BASE -> drawHitCircle(shapes, (HitCircleVisual) command.object(), state, viewport);
                case NUMBER -> {
                    HitCircleVisual circle = (HitCircleVisual) command.object();
                    sprites(shapes, () -> hud.drawComboNumber(batch, circle.comboNumber(), circle.x(), circle.y(),
                            circle.radius() * numberScale(circle.judgement(), circle.judgementTimeMs(), state.currentTimeMs()),
                            numberAlpha(circle.judgement(), circle.judgementTimeMs(), state.currentTimeMs(), circleInitialAlpha(circle, state)), viewport));
                }
                case CIRCLE_OVERLAY -> {
                    HitCircleVisual circle = (HitCircleVisual) command.object();
                    shapeType(shapes, ShapeRenderer.ShapeType.Line);
                    drawSkinnedCircleOverlay(shapes, Image.HIT_CIRCLE_OVERLAY, circle.x(), circle.y(),
                            circle.radius() * circleScale(circle, state), circleAlpha(circle, state), viewport);
                    shapeType(shapes, ShapeRenderer.ShapeType.Filled);
                }
                case SLIDER_BODY -> drawSliderBody(shapes, slider, state, viewport);
                case TAIL_BASE -> drawSliderTail(shapes, slider, state, viewport);
                case TAIL_OVERLAY -> {
                    shapeType(shapes, ShapeRenderer.ShapeType.Line);
                    drawSliderTailOverlay(shapes, slider, state, viewport);
                    shapeType(shapes, ShapeRenderer.ShapeType.Filled);
                }
                case TICKS -> drawSliderTicks(shapes, slider, state, viewport);
                case REPEATS -> drawSliderRepeats(shapes, slider, state, viewport);
                case HEAD_BASE -> drawSliderHead(shapes, slider, state, viewport);
                case REVERSE_ARROWS -> drawSliderArrows(shapes, slider, state, viewport);
                case HEAD_NUMBER -> {
                    sprites(shapes, () -> hud.drawComboNumber(batch,
                            slider.comboNumber(), slider.headPosition().x(), slider.headPosition().y(),
                            slider.radius() * numberScale(headJudgement(slider), slider.headJudgementTimeMs(), state.currentTimeMs()),
                            numberAlpha(headJudgement(slider), slider.headJudgementTimeMs(), state.currentTimeMs(), headInitialAlpha(slider, state)), viewport));
                }
                case HEAD_OVERLAY -> {
                    shapeType(shapes, ShapeRenderer.ShapeType.Line);
                    drawSliderHeadOverlay(shapes, slider, state, viewport);
                    shapeType(shapes, ShapeRenderer.ShapeType.Filled);
                }
                case BALL_AND_FOLLOW -> drawSliderBallAndFollow(shapes, slider, state, viewport);
                case JUDGEMENT_ABOVE -> { /* Reserved judgement layer; no custom expanding ring. */ }
                case APPROACH -> {
                    shapeType(shapes, ShapeRenderer.ShapeType.Line);
                    if (command.object() instanceof HitCircleVisual circle && circle.judgement() == null)
                        drawApproach(shapes, circle.x(), circle.y(), circle.radius(), circle.timeMs(),
                                circle.preemptMs(), state.currentTimeMs(), visuals.comboColor(circle.comboColorIndex()), viewport);
                    else if (slider != null && !slider.headJudged() && state.currentTimeMs() < slider.startTimeMs())
                        drawApproach(shapes, slider.headPosition().x(), slider.headPosition().y(), slider.radius(),
                                slider.startTimeMs(), slider.preemptMs(), state.currentTimeMs(),
                                visuals.comboColor(slider.comboColorIndex()), viewport);
                    shapeType(shapes, ShapeRenderer.ShapeType.Filled);
                }
                case HUD -> {
                    if (debugCursor != null) {
                        shapeType(shapes, ShapeRenderer.ShapeType.Line);
                        drawDebugCursor(shapes, debugCursor, viewport);
                        shapeType(shapes, ShapeRenderer.ShapeType.Filled);
                    }
                    hud.drawPanels(shapes, viewport);
                    sprites(shapes, () -> hud.draw(batch, set, difficulty, state, viewport, notice));
                }
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        pruneSliderRenderData(state.currentTimeMs());
    }

    private void shapeType(ShapeRenderer shapes, ShapeRenderer.ShapeType type) {
        if (shapes.getCurrentType() == type) return;
        shapes.end();
        shapes.begin(type);
    }

    /** Flushes before changing drawing backend; submission order is the command order. */
    private void sprites(ShapeRenderer shapes, Runnable draw) {
        ShapeRenderer.ShapeType type = shapes.getCurrentType();
        shapes.end();
        SpriteBatch batch = game.batch();
        batch.setColor(Color.WHITE);
        batch.begin();
        draw.run();
        batch.end();
        batch.setColor(Color.WHITE);
        game.font().setColor(Color.WHITE);
        game.font().getData().setScale(1f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(type);
    }

    private float circleInitialAlpha(HitCircleVisual circle, GameplayState state) {
        boolean hit = circle.judgement() != null && circle.judgement() != Judgement.MISS;
        return (float) GameplayVisualTiming.fadeInProgress(hit ? circle.judgementTimeMs() : state.currentTimeMs(),
                circle.timeMs(), circle.preemptMs(), ApproachTimeCalculator.fadeInMs(circle.preemptMs()));
    }

    private float circleAlpha(HitCircleVisual circle, GameplayState state) {
        return circleInitialAlpha(circle, state)
                * (float) judgementAlpha(circle.judgement(), circle.judgementTimeMs(), state.currentTimeMs());
    }

    private double circleScale(HitCircleVisual circle, GameplayState state) {
        return judgementScale(circle.judgement(), circle.judgementTimeMs(), state.currentTimeMs());
    }

    private Judgement headJudgement(SliderVisual slider) {
        return !slider.headJudged() ? null : slider.headHit() ? Judgement.HIT300 : Judgement.MISS;
    }

    private double judgementScale(Judgement judgement, double time, long now) {
        return judgement == null || judgement == Judgement.MISS ? 1 : GameplayVisualTiming.hitCircleScale(now, time);
    }

    private double judgementAlpha(Judgement judgement, double time, long now) {
        return judgement == null ? 1 : judgement == Judgement.MISS
                ? GameplayVisualTiming.hitCircleMissAlpha(now, time) : GameplayVisualTiming.hitCircleAlpha(now, time);
    }

    private double numberScale(Judgement judgement, double time, long now) {
        return judgement == null || judgement == Judgement.MISS ? 1
                : GameplayVisualTiming.hitCircleNumberScale(now, time, skinAssets == null ? 1 : skinAssets.legacyVersion());
    }

    private float numberAlpha(Judgement judgement, double time, long now, float initialAlpha) {
        if (judgement == null) return initialAlpha;
        return initialAlpha * (float) (judgement == Judgement.MISS ? GameplayVisualTiming.hitCircleMissAlpha(now, time)
                : GameplayVisualTiming.hitCircleNumberAlpha(now, time, skinAssets == null ? 1 : skinAssets.legacyVersion()));
    }

    private float headInitialAlpha(SliderVisual slider, GameplayState state) {
        return (float) (GameplayVisualTiming.fadeInProgress(slider.headJudged() && slider.headHit()
                        ? slider.headJudgementTimeMs() : state.currentTimeMs(), slider.startTimeMs(),
                slider.preemptMs(), ApproachTimeCalculator.fadeInMs(slider.preemptMs()))
                * GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.endTimeMs(), 240));
    }

    private float headAlpha(SliderVisual slider, GameplayState state) {
        return headInitialAlpha(slider, state)
                * (float) judgementAlpha(headJudgement(slider), slider.headJudgementTimeMs(), state.currentTimeMs());
    }

    private float sliderAlpha(SliderVisual slider, GameplayState state) {
        return (float) (GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                slider.preemptMs(), ApproachTimeCalculator.fadeInMs(slider.preemptMs()))
                * GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.endTimeMs(), SLIDER_POST_FADE_MS));
    }

    private void drawDebugCursor(ShapeRenderer shapes, BeatmapPoint cursor, PlayfieldViewport viewport) {
        float x = viewport.toScreenX(cursor.x());
        float y = viewport.toScreenY(cursor.y());
        float radius = viewport.toScreenLength(8);
        shapes.setColor(.42f, 1f, .79f, .96f);
        shapes.circle(x, y, radius, 24);
        shapes.line(x - radius * 1.6f, y, x + radius * 1.6f, y);
        shapes.line(x, y - radius * 1.6f, x, y + radius * 1.6f);
    }

    private void drawHitCircle(ShapeRenderer shapes, HitCircleVisual circle,
                               GameplayState state, PlayfieldViewport viewport) {
        drawSkinnedCircleBody(shapes, Image.HIT_CIRCLE, circle.x(), circle.y(),
                circle.radius() * circleScale(circle, state), visuals.comboColor(circle.comboColorIndex()),
                circleAlpha(circle, state), viewport);
    }

    private void drawSkinnedCircleBody(ShapeRenderer shapes, Image image, double x, double y, double radius,
                                       Color tint, float alpha, PlayfieldViewport viewport) {
        if (drawSkinImage(shapes, image, x, y, radius, tint, alpha, viewport)) return;
        drawCircleBody(shapes, viewport.toScreenX(x), viewport.toScreenY(y),
                viewport.toScreenLength(radius), tint, alpha);
    }

    private void drawSkinnedCircleOverlay(ShapeRenderer shapes, Image image, double x, double y, double radius,
                                          float alpha, PlayfieldViewport viewport) {
        // osu! legacy overlays keep their source colours, independently of the combo colour.
        if (drawSkinImage(shapes, image, x, y, radius, Color.WHITE, alpha, viewport)) return;
        drawCircleOverlay(shapes, x, y, radius, alpha, viewport);
    }

    /** Draw exactly one texture or fallback at this position in the object-local command sequence. */
    private boolean drawSkinImage(ShapeRenderer shapes, Image image, double x, double y, double radius,
                                  Color tint, float alpha, PlayfieldViewport viewport) {
        return drawSkinImage(shapes, image, x, y, radius, tint, alpha, 0, viewport);
    }

    private boolean drawSkinImage(ShapeRenderer shapes, Image image, double x, double y, double radius,
                                  Color tint, float alpha, double rotationDegrees, PlayfieldViewport viewport) {
        SkinTexture asset = skinAssets == null ? null : skinAssets.get(image);
        // A missing overlay for a dedicated slider prefix is intentionally an empty layer.
        if (asset == null) return skinAssets != null && skinAssets.hasDedicatedSliderCircle(image);
        if (alpha <= 0.01f) return true;

        // Legacy circle sprites have a 128-logical-pixel reference diameter. Density is applied
        // before CS/viewport scaling; padded or non-square images retain their proportions.
        float scale = viewport.toScreenLength(radius * 2) / 128f;
        if (image == Image.SLIDER_FOLLOW_CIRCLE) scale *= 0.5f;
        float width = asset.logicalWidth() * scale;
        float height = asset.logicalHeight() * scale;
        sprites(shapes, () -> {
            SpriteBatch batch = game.batch();
            batch.setColor(tint.r, tint.g, tint.b, tint.a * alpha);
            batch.draw(asset.texture(), viewport.toScreenX(x) - width / 2,
                    viewport.toScreenY(y) - height / 2, width / 2, height / 2, width, height,
                    1, 1, (float) -rotationDegrees, 0, 0, asset.texture().getWidth(), asset.texture().getHeight(), false, false);
        });
        return true;
    }

    private void drawSliderBody(ShapeRenderer shapes, SliderVisual slider,
                                GameplayState state, PlayfieldViewport viewport) {
        SliderRenderData renderData = renderData(slider, state.currentTimeMs());
        double fadeIn = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), slider.startTimeMs(),
                slider.preemptMs(), ApproachTimeCalculator.fadeInMs(slider.preemptMs()));
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), slider.endTimeMs(), SLIDER_POST_FADE_MS);
        float alpha = (float) (fadeIn * fadeOut);
        float radius = viewport.toScreenLength(slider.radius());
        Color comboColor = visuals.comboColor(slider.comboColorIndex());
        double snake = GameplayVisualTiming.sliderSnakeProgress(state.currentTimeMs(),
                slider.startTimeMs(), slider.preemptMs());
        drawThickPath(shapes, renderData, viewport, radius * 2.02f, visuals.component(GameplaySkinComponent.SLIDER_BORDER), alpha, snake);
        drawThickPath(shapes, renderData, viewport, radius * 1.94f, comboColor, alpha, snake);
        drawThickPath(shapes, renderData, viewport, radius * 1.70f, visuals.component(GameplaySkinComponent.SLIDER_INNER), alpha, snake);
    }

    private void drawThickPath(ShapeRenderer shapes, SliderRenderData geometry, PlayfieldViewport viewport,
                               float width, Color color, float alpha, double progress) {
        double[] points = geometry.points;
        if (points.length < 2 || progress <= 0) return;
        setColor(shapes, color, alpha);
        float firstX = viewport.toScreenX(points[0]);
        float firstY = viewport.toScreenY(points[1]);
        float capRadius = width * 0.5f;
        double limit = geometry.totalDistance * GameplayVisualTiming.clamp(progress);
        shapes.circle(firstX, firstY, capRadius, BODY_CAP_SEGMENTS);
        for (int index = 2; index < points.length; index += 2) {
            int pointIndex = index / 2;
            double before = geometry.cumulativeDistance[pointIndex - 1];
            if (before >= limit) break;
            double segment = geometry.cumulativeDistance[pointIndex] - before;
            double fraction = segment <= 0 ? 1 : Math.min(1, (limit - before) / segment);
            float x = viewport.toScreenX(points[index - 2] + (points[index] - points[index - 2]) * fraction);
            float y = viewport.toScreenY(points[index - 1] + (points[index + 1] - points[index - 1]) * fraction);
            shapes.rectLine(firstX, firstY, x, y, width);
            shapes.circle(x, y, capRadius, BODY_CAP_SEGMENTS);
            firstX = x;
            firstY = y;
            if (fraction < 1) break;
        }
    }

    private void drawSliderTail(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        float bodyAlpha = sliderAlpha(slider, state);
        Color comboColor = visuals.comboColor(slider.comboColorIndex());
        double tailFadeIn = slider.tailVisualTiming().alphaAt(now);
        float tailAlpha = (float) (bodyAlpha * tailFadeIn);
        drawSkinnedCircleBody(shapes, Image.SLIDER_END_CIRCLE, slider.tailPosition().x(), slider.tailPosition().y(),
                slider.radius(), comboColor, tailAlpha, viewport);
    }

    private void drawSliderRepeats(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        float bodyAlpha = sliderAlpha(slider, state);
        float radius = viewport.toScreenLength(slider.radius());
        Color comboColor = visuals.comboColor(slider.comboColorIndex());
        for (SliderVisual.RepeatMarker repeat : slider.repeats()) {
            double markerFadeOut = repeatFadeOut(slider, repeat, now);
            float alpha = (float) (bodyAlpha * repeat.visualTiming().alphaAt(now) * markerFadeOut);
            float x = viewport.toScreenX(repeat.position().x());
            float y = viewport.toScreenY(repeat.position().y());
            drawCircleBody(shapes, x, y, radius * 0.37f,
                    repeat.judged() ? visuals.component(GameplaySkinComponent.SLIDER_INNER) : comboColor, alpha);
            shapeType(shapes, ShapeRenderer.ShapeType.Line);
            setColor(shapes, visuals.component(GameplaySkinComponent.HITCIRCLE_BORDER), alpha);
            shapes.circle(x, y, radius * 0.37f, CIRCLE_SEGMENTS - 8);
            shapeType(shapes, ShapeRenderer.ShapeType.Filled);
        }
    }

    private void drawSliderTicks(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        for (SliderVisual.TickMarker tick : slider.ticks()) {
            double scale = SliderNestedAnimation.tickScale(now, tick.visualTiming(), tick.judged(), tick.hit(), tick.judgementTimeMs());
            float alpha = (float) (SliderNestedAnimation.tickAlpha(now, tick.visualTiming(), tick.judged(), tick.judgementTimeMs())
                    * GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), 240));
            if (drawSkinImage(shapes, Image.SLIDER_TICK, tick.position().x(), tick.position().y(),
                    slider.radius() * scale, Color.WHITE, alpha, viewport)) continue;
            setColor(shapes, visuals.component(GameplaySkinComponent.HITCIRCLE_BORDER), alpha);
            shapes.circle(viewport.toScreenX(tick.position().x()), viewport.toScreenY(tick.position().y()),
                    viewport.toScreenLength(slider.radius() * 16 / 128 * scale), 16);
        }
    }

    private void drawSliderHead(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        drawSkinnedCircleBody(shapes, Image.SLIDER_START_CIRCLE, slider.headPosition().x(), slider.headPosition().y(),
                slider.radius() * judgementScale(headJudgement(slider), slider.headJudgementTimeMs(), state.currentTimeMs()),
                visuals.comboColor(slider.comboColorIndex()), headAlpha(slider, state), viewport);
    }

    private void drawSliderArrows(ShapeRenderer shapes, SliderVisual slider,
                                   GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        float parentAlpha = (float) GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), 240);
        float radius = viewport.toScreenLength(slider.radius());
        for (SliderVisual.RepeatMarker repeat : slider.repeats()) {
            double markerFadeOut = repeatFadeOut(slider, repeat, now);
            float alpha = (float) (parentAlpha * repeat.reverseArrowTiming().alphaAt(now) * markerFadeOut);
            drawReverseArrow(shapes, slider, repeat, viewport.toScreenX(repeat.position().x()),
                    viewport.toScreenY(repeat.position().y()), radius, alpha, now, viewport);
        }
    }

    private double repeatFadeOut(SliderVisual slider, SliderVisual.RepeatMarker repeat, long now) {
        double spanDuration = Math.max(1, (slider.endTimeMs() - slider.startTimeMs()) / (slider.repeats().size() + 1));
        return repeat.judged() ? SliderNestedAnimation.repeatAlpha(now, repeat.judgementTimeMs(), spanDuration, repeat.hit())
                : now >= repeat.timeMs() ? 0 : 1;
    }

    private void drawReverseArrow(ShapeRenderer shapes, SliderVisual slider, SliderVisual.RepeatMarker repeat,
                                  float centerX, float centerY, float circleRadius, float alpha,
                                  long now, PlayfieldViewport viewport) {
        if (alpha <= 0.01f || slider.pathPoints().size() < 2) return;
        boolean oldSkin = skinAssets != null && skinAssets.legacyVersion() <= 1;
        double spanDuration = (slider.endTimeMs() - slider.startTimeMs()) / (slider.repeats().size() + 1);
        double animationStart = repeat.visualTiming().lifetimeStartTimeMs();
        double scale = SliderNestedAnimation.arrowScale(now, animationStart, repeat.judgementTimeMs(),
                spanDuration, repeat.judged() && repeat.hit(), oldSkin);
        double degrees = repeat.rotationDegrees() + SliderNestedAnimation.arrowWobble(
                repeat.judged() && repeat.hit() ? repeat.judgementTimeMs() : now, animationStart, oldSkin);
        if (drawSkinImage(shapes, Image.REVERSE_ARROW, repeat.position().x(), repeat.position().y(),
                slider.radius() * scale, Color.WHITE, alpha, degrees, viewport)) return;
        double angle = Math.toRadians(-degrees);
        float size = circleRadius * 0.24f * (float) scale;
        float x = centerX;
        float y = centerY;
        setColor(shapes, visuals.component(GameplaySkinComponent.HITCIRCLE_BORDER), alpha);
        drawArrowTriangle(shapes, x, y, size * 1.18f, angle);
        setColor(shapes, visuals.component(GameplaySkinComponent.SLIDER_INNER), alpha);
        drawArrowTriangle(shapes, x, y, size * 0.72f, angle);
    }

    private void drawArrowTriangle(ShapeRenderer shapes, float centerX, float centerY, float size, double angle) {
        float tipX = centerX + (float) Math.cos(angle) * size;
        float tipY = centerY + (float) Math.sin(angle) * size;
        float baseX = centerX - (float) Math.cos(angle) * size * 0.68f;
        float baseY = centerY - (float) Math.sin(angle) * size * 0.68f;
        float sideX = (float) -Math.sin(angle) * size * 0.58f;
        float sideY = (float) Math.cos(angle) * size * 0.58f;
        shapes.triangle(tipX, tipY, baseX + sideX, baseY + sideY, baseX - sideX, baseY - sideY);
    }

    private void drawSliderBallAndFollow(ShapeRenderer shapes, SliderVisual slider,
                                         GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        if (now < slider.startTimeMs() || now > slider.endTimeMs() + 240) return;
        var follow = FollowCircleAnimation.at(slider.followEvents(), now, slider.endTimeMs());
        float x = viewport.toScreenX(slider.ballPosition().x());
        float y = viewport.toScreenY(slider.ballPosition().y());
        float radius = viewport.toScreenLength(slider.radius());
        float followAlpha = (float) (follow.alpha() * GameplayVisualTiming.fadeOutAlpha(now, slider.endTimeMs(), 240));
        float followRadius = radius * (float) follow.scale();
        if (followAlpha > 0.01f && !drawSkinImage(shapes, Image.SLIDER_FOLLOW_CIRCLE,
                slider.ballPosition().x(), slider.ballPosition().y(), slider.radius() * follow.scale(),
                Color.WHITE, followAlpha, viewport)) {
            shapeType(shapes, ShapeRenderer.ShapeType.Line);
            setColor(shapes, visuals.component(GameplaySkinComponent.FOLLOWCIRCLE_BORDER), followAlpha);
            shapes.circle(x, y, followRadius, CIRCLE_SEGMENTS);
            shapeType(shapes, ShapeRenderer.ShapeType.Filled);
        }

        // LegacySliderBall instantly disappears at the actual slider end.
        if (now >= slider.endTimeMs()) return;
        if (drawSkinSliderBall(shapes, slider, now, 1, viewport)) return;
        float ballRadius = radius * 0.54f;
        drawCircleBody(shapes, x, y, ballRadius, visuals.comboColor(slider.comboColorIndex()), 1);
        setColor(shapes, visuals.component(GameplaySkinComponent.SLIDERBALL), slider.tracking() ? 0.7f : 0.38f);
        shapes.circle(x, y, ballRadius * 0.48f, CIRCLE_SEGMENTS);
    }

    private boolean drawSkinSliderBall(ShapeRenderer shapes, SliderVisual slider, long now,
                                       float alpha, PlayfieldViewport viewport) {
        if (skinAssets == null) return false;
        List<SkinTexture> frames = skinAssets.sliderBallFrames();
        int index = LegacySliderBallAnimation.frameIndex(frames.size(), now,
                slider.startTimeMs(), slider.preemptMs(), slider.velocity());
        if (index < 0) return false;
        if (alpha <= 0.01f) return true;
        SkinTexture asset = frames.get(index);
        Texture texture = asset.texture();
        var size = LegacySliderBallAnimation.spriteSize(texture.getWidth(), texture.getHeight(),
                asset.density(), slider.radius(), viewport.scale());
        sprites(shapes, () -> {
            SpriteBatch batch = game.batch();
            // LegacySliderBall defaults to white. Rotation is gameplay-supplied visual data.
            batch.setColor(1, 1, 1, alpha);
            TextureRegion region = new TextureRegion(texture);
            // TextureRegion stores top/bottom V in the reverse order of the raw SpriteBatch UV overload.
            region.setRegion(size.u(), size.v2(), size.u2(), size.v());
            batch.draw(region, viewport.toScreenX(slider.ballPosition().x()) - size.width() / 2,
                    viewport.toScreenY(slider.ballPosition().y()) - size.height() / 2,
                    size.width() / 2, size.height() / 2, size.width(), size.height(), 1, 1,
                    (float) -slider.ballRotationDegrees());
        });
        return true;
    }

    private void drawSpinnerField(ShapeRenderer shapes, SpinnerVisual spinner,
                                  GameplayState state, PlayfieldViewport viewport) {
        double fadeIn = GameplayVisualTiming.fadeInProgress(state.currentTimeMs(), spinner.startTimeMs(),
                spinner.preemptMs(), ApproachTimeCalculator.fadeInMs(spinner.preemptMs()));
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(state.currentTimeMs(), spinner.endTimeMs(), SPINNER_POST_FADE_MS);
        float alpha = (float) (fadeIn * fadeOut);
        float x = viewport.toScreenX(spinner.centerX());
        float y = viewport.toScreenY(spinner.centerY());
        double intro = GameplayVisualTiming.spinnerIntroScale(state.currentTimeMs(), spinner.startTimeMs(), spinner.preemptMs());
        double finish = state.currentTimeMs() >= spinner.endTimeMs()
                ? GameplayVisualTiming.spinnerCompletionScale(state.currentTimeMs(), spinner.endTimeMs(),
                spinner.judgement() != Judgement.MISS) : 1;
        float radius = viewport.toScreenLength(spinner.radius() * intro * finish);
        setColor(shapes, visuals.component(GameplaySkinComponent.SPINNER_FIELD), alpha);
        shapes.circle(x, y, radius * 1.14f, CIRCLE_SEGMENTS + 12);
        setColor(shapes, spinner.completionTimeMs() != Long.MIN_VALUE ? visuals.component(GameplaySkinComponent.SPINNER_COMPLETE) : visuals.component(GameplaySkinComponent.SPINNER_PROGRESS),
                alpha * 0.18f);
        shapes.circle(x, y, radius * 1.07f, CIRCLE_SEGMENTS + 12);
        setColor(shapes, visuals.component(GameplaySkinComponent.SPINNER_FIELD), alpha * 0.88f);
        shapes.circle(x, y, radius * 0.97f, CIRCLE_SEGMENTS + 12);
        float fillRadius = radius * (float) (0.2 + 0.75 * GameplayVisualTiming.clamp(spinner.progress()));
        setColor(shapes, spinner.completionTimeMs() != Long.MIN_VALUE ? visuals.component(GameplaySkinComponent.SPINNER_COMPLETE) : visuals.component(GameplaySkinComponent.SPINNER_PROGRESS),
                alpha * (spinner.tracking() ? 0.40f : 0.20f));
        shapes.circle(x, y, fillRadius, CIRCLE_SEGMENTS + 12);
        setColor(shapes, visuals.component(GameplaySkinComponent.SLIDER_INNER), alpha);
        shapes.circle(x, y, radius * (spinner.tracking() ? 0.13f : 0.18f), CIRCLE_SEGMENTS);
    }

    private void drawApproach(ShapeRenderer shapes, double x, double y, double radius,
                              long objectTimeMs, long preemptMs, long now, Color tint, PlayfieldViewport viewport) {
        double progress = GameplayVisualTiming.approachProgress(now, objectTimeMs, preemptMs);
        float alpha = (float) GameplayVisualTiming.approachAlpha(now, objectTimeMs, preemptMs);
        double logicalRadius = GameplayVisualTiming.approachRadius(radius, progress);
        if (drawSkinImage(shapes, Image.APPROACH_CIRCLE, x, y, logicalRadius, tint, alpha, viewport)) return;
        float approachRadius = viewport.toScreenLength(logicalRadius);
        setColor(shapes, visuals.component(GameplaySkinComponent.APPROACHCIRCLE), alpha);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y), approachRadius, CIRCLE_SEGMENTS + 4);
    }

    private void drawSliderHeadOverlay(ShapeRenderer shapes, SliderVisual slider,
                                       GameplayState state, PlayfieldViewport viewport) {
        drawSkinnedCircleOverlay(shapes, Image.SLIDER_START_CIRCLE_OVERLAY, slider.headPosition().x(),
                slider.headPosition().y(), slider.radius() * judgementScale(headJudgement(slider),
                        slider.headJudgementTimeMs(), state.currentTimeMs()), headAlpha(slider, state), viewport);
    }

    private void drawCircleOverlay(ShapeRenderer shapes, double x, double y, double radius,
                                   float alpha, PlayfieldViewport viewport) {
        setColor(shapes, visuals.component(GameplaySkinComponent.HITCIRCLE_BORDER), alpha * 0.65f);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y),
                viewport.toScreenLength(radius * 0.98), CIRCLE_SEGMENTS);
        setColor(shapes, visuals.component(GameplaySkinComponent.HITCIRCLE_OVERLAY), alpha);
        shapes.circle(viewport.toScreenX(x), viewport.toScreenY(y),
                viewport.toScreenLength(radius * 0.73), CIRCLE_SEGMENTS - 4);
    }

    private void drawSliderTailOverlay(ShapeRenderer shapes, SliderVisual slider,
                               GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        float radius = viewport.toScreenLength(slider.radius());
        double bodyFade = sliderAlpha(slider, state);
        Color comboColor = visuals.comboColor(slider.comboColorIndex());
        double tailAlpha = bodyFade * slider.tailVisualTiming().alphaAt(now);
        if (!drawSkinImage(shapes, Image.SLIDER_END_CIRCLE_OVERLAY, slider.tailPosition().x(), slider.tailPosition().y(),
                slider.radius(), Color.WHITE, (float) tailAlpha, viewport)) {
            setColor(shapes, comboColor, (float) tailAlpha * 0.72f);
            shapes.circle(viewport.toScreenX(slider.tailPosition().x()), viewport.toScreenY(slider.tailPosition().y()),
                    radius, CIRCLE_SEGMENTS);
        }
    }

    private void drawSpinnerOverlay(ShapeRenderer shapes, SpinnerVisual spinner, GameplayState state, PlayfieldViewport viewport) {
        long now = state.currentTimeMs();
        double fadeIn = GameplayVisualTiming.fadeInProgress(now, spinner.startTimeMs(), spinner.preemptMs(),
                ApproachTimeCalculator.fadeInMs(spinner.preemptMs()));
        double fadeOut = GameplayVisualTiming.fadeOutAlpha(now, spinner.endTimeMs(), SPINNER_POST_FADE_MS);
        float alpha = (float) (fadeIn * fadeOut);
        float centerX = viewport.toScreenX(spinner.centerX());
        float centerY = viewport.toScreenY(spinner.centerY());
        double intro = GameplayVisualTiming.spinnerIntroScale(now, spinner.startTimeMs(), spinner.preemptMs());
        double finish = now >= spinner.endTimeMs()
                ? GameplayVisualTiming.spinnerCompletionScale(now, spinner.endTimeMs(),
                spinner.judgement() != Judgement.MISS) : 1;
        float radius = viewport.toScreenLength(spinner.radius() * intro * finish);
        Color progressColor = spinner.judgement() == Judgement.MISS ? visuals.component(GameplaySkinComponent.SPINNER_MISS)
                : spinner.completionTimeMs() != Long.MIN_VALUE ? visuals.component(GameplaySkinComponent.SPINNER_COMPLETE) : visuals.component(GameplaySkinComponent.SPINNER_PROGRESS);
        setColor(shapes, visuals.component(GameplaySkinComponent.SPINNER_RING), alpha);
        shapes.circle(centerX, centerY, radius, CIRCLE_SEGMENTS + 12);
        int markers = Math.max(1, Math.min(32, spinner.requiredSpins()));
        for (int i = 0; i < markers; i++) {
            double angle = Math.PI * 2 * i / markers;
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);
            setColor(shapes, i < spinner.completedSpins() ? visuals.component(GameplaySkinComponent.SPINNER_COMPLETE) : visuals.component(GameplaySkinComponent.SPINNER_RING), alpha);
            shapes.line(centerX + cos * radius * 0.87f, centerY + sin * radius * 0.87f,
                    centerX + cos * radius * 0.96f, centerY + sin * radius * 0.96f);
        }
        double progress = GameplayVisualTiming.clamp(spinner.progress());
        if (progress > 0) {
            setColor(shapes, progressColor, alpha);
            int segments = Math.max(1, (int) Math.ceil(72 * progress));
            for (int stroke = 0; stroke < 5; stroke++) {
                shapes.arc(centerX, centerY, radius * 1.045f - stroke * 1.7f, 90,
                        (float) (360 * progress), segments);
            }
        }

        // Gameplay angles use osu!'s downward-positive Y axis; the viewport is upward-positive.
        double angle = Math.toRadians(-spinner.rotationDegrees());
        float innerX = centerX + (float) Math.cos(angle) * radius * 0.25f;
        float innerY = centerY + (float) Math.sin(angle) * radius * 0.25f;
        float indicatorX = centerX + (float) Math.cos(angle) * radius * 0.86f;
        float indicatorY = centerY + (float) Math.sin(angle) * radius * 0.86f;
        setColor(shapes, spinner.tracking() ? visuals.component(GameplaySkinComponent.SPINNER_COMPLETE) : visuals.component(GameplaySkinComponent.SPINNER_RING), alpha);
        shapes.line(innerX, innerY, indicatorX, indicatorY);
    }

    private void drawCircleBody(ShapeRenderer shapes, float x, float y, float radius,
                                Color fill, float alpha) {
        if (alpha <= 0.01f) return;
        setColor(shapes, visuals.component(GameplaySkinComponent.SLIDER_BORDER), alpha);
        shapes.circle(x, y, radius * 1.08f, CIRCLE_SEGMENTS);
        setColor(shapes, visuals.component(GameplaySkinComponent.HITCIRCLE_BORDER), alpha);
        shapes.circle(x, y, radius * 0.99f, CIRCLE_SEGMENTS);
        setColor(shapes, fill, alpha);
        shapes.circle(x, y, radius * 0.91f, CIRCLE_SEGMENTS);
    }

    private void setColor(ShapeRenderer shapes, Color color, float alphaMultiplier) {
        shapes.setColor(color.r, color.g, color.b, color.a * alphaMultiplier);
    }

    private SliderRenderData renderData(SliderVisual slider, long now) {
        List<BeatmapPoint> path = slider.pathPoints();
        SliderRenderData data = sliderRenderData.get(path);
        if (data == null) {
            double[] points = new double[path.size() * 2];
            int index = 0;
            for (BeatmapPoint point : path) {
                points[index++] = point.x();
                points[index++] = point.y();
            }
            data = new SliderRenderData(points);
            sliderRenderData.put(path, data);
        }
        data.lastUsedTimeMs = now;
        return data;
    }

    private void pruneSliderRenderData(long now) {
        Iterator<SliderRenderData> entries = sliderRenderData.values().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().lastUsedTimeMs > 5000) entries.remove();
        }
    }

    private static final class SliderRenderData {
        private final double[] points;
        private final double[] cumulativeDistance;
        private final double totalDistance;
        private long lastUsedTimeMs;

        private SliderRenderData(double[] points) {
            this.points = points;
            cumulativeDistance = new double[points.length / 2];
            for (int i = 1; i < cumulativeDistance.length; i++) {
                cumulativeDistance[i] = cumulativeDistance[i - 1]
                        + Math.hypot(points[2 * i] - points[2 * i - 2], points[2 * i + 1] - points[2 * i - 1]);
            }
            totalDistance = cumulativeDistance.length == 0 ? 0 : cumulativeDistance[cumulativeDistance.length - 1];
        }
    }

}
