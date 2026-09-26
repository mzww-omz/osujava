package dev.osujava.ruleset.osu.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import dev.osujava.ui.PlayfieldViewport;
import java.nio.IntBuffer;

/** Distance-resolved Body buffer, composited once at the parent's render-plan position. */
public final class SliderBodyRenderer implements Disposable {
    private static final String VERTEX = """
            attribute vec2 a_position;
            attribute vec2 a_start;
            attribute vec2 a_end;
            attribute vec2 a_distance;
            uniform mat4 u_projection;
            uniform vec3 u_viewport;
            uniform float u_radius;
            uniform float u_limit;
            varying vec2 v_point;
            varying vec2 v_start;
            varying vec2 v_end;
            void main() {
                float size = a_distance.y - a_distance.x;
                float fraction = size > 0.0 ? clamp((u_limit-a_distance.x)/size,0.0,1.0) : 0.0;
                v_start = a_start;
                v_end = mix(a_start,a_end,fraction);
                vec2 direction = a_end-a_start;
                float len = length(direction);
                direction = len > 0.0 ? direction/len : vec2(1.0,0.0);
                vec2 normal = vec2(-direction.y,direction.x);
                v_point = mix(v_start,v_end,(a_position.x+1.0)*0.5)
                    + u_radius*(direction*a_position.x + normal*a_position.y);
                vec2 screen = vec2(u_viewport.x+v_point.x*u_viewport.z,
                    u_viewport.y+(384.0-v_point.y)*u_viewport.z);
                gl_Position = u_projection * vec4(screen,0.0,1.0);
            }
            """;
    private static final String FRAGMENT = """
            #ifdef GL_ES
            #extension GL_EXT_frag_depth : require
            precision highp float;
            #define WRITE_DEPTH gl_FragDepthEXT
            #else
            #define WRITE_DEPTH gl_FragDepth
            #endif
            varying vec2 v_point;
            varying vec2 v_start;
            varying vec2 v_end;
            uniform float u_radius;
            uniform vec4 u_border;
            uniform vec4 u_outer;
            uniform vec4 u_inner;
            uniform float u_shadowPortion;
            uniform float u_borderPortion;
            void main() {
                vec2 delta = v_end-v_start;
                float squared = dot(delta,delta);
                float t = squared > 0.0 ? clamp(dot(v_point-v_start,delta)/squared,0.0,1.0) : 0.0;
                float distance = length(v_point-v_start-delta*t)/u_radius;
                if (distance >= 1.0) discard;
                // Closest centreline wins, including intersecting segments and round joins.
                WRITE_DEPTH = distance;
                float p = 1.0-distance;
                vec4 colour;
                if (p <= u_shadowPortion) colour = vec4(0.0,0.0,0.0,0.25*p/u_shadowPortion);
                else if (p <= u_borderPortion) colour = u_border;
                else colour = mix(u_outer,u_inner,(p-u_borderPortion)/(1.0-u_borderPortion));
                colour.a *= min(p/0.02,1.0);
                gl_FragColor = colour;
            }
            """;

    private ShaderProgram shader;
    private FrameBuffer buffer;
    private TextureRegion region;
    private final IntBuffer binding = BufferUtils.newIntBuffer(1);
    private final IntBuffer scissorState = BufferUtils.newIntBuffer(4);
    private final IntBuffer viewportState = BufferUtils.newIntBuffer(4);

    public static Mesh mesh(SliderBodyGeometry geometry) {
        float[] vertices = geometry.vertices();
        Mesh mesh = new Mesh(true, vertices.length / SliderBodyGeometry.FLOATS_PER_VERTEX, 0,
                new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_start"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_end"),
                new VertexAttribute(VertexAttributes.Usage.Generic, 2, "a_distance"));
        mesh.setVertices(vertices);
        return mesh;
    }

    public void draw(Mesh mesh, SliderBodyGeometry geometry, double snake, double radius,
                     Color border, Color track, float alpha, PlayfieldViewport viewport,
                     Matrix4 projection, SpriteBatch batch) {
        int count = geometry.visibleSegments(snake) * SliderBodyGeometry.VERTICES_PER_SEGMENT;
        if (count == 0 || alpha <= 0 || radius <= 0) return;
        if (shader == null) {
            shader = new ShaderProgram(VERTEX, FRAGMENT);
            if (!shader.isCompiled()) throw new IllegalStateException("Slider Body shader: " + shader.getLog());
        }
        binding.clear(); viewportState.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, binding);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, viewportState);
        int previousBuffer = binding.get(0);
        int width = viewportState.get(2), height = viewportState.get(3);
        // Projection coordinates are logical pixels; scissor/texture coordinates are framebuffer pixels.
        float pixelScaleX = width * projection.val[Matrix4.M00] * .5f;
        float pixelScaleY = height * projection.val[Matrix4.M11] * .5f;
        float pixelOriginX = width * (projection.val[Matrix4.M03] + 1) * .5f;
        float pixelOriginY = height * (projection.val[Matrix4.M13] + 1) * .5f;
        var bounds = geometry.bounds();
        float r = viewport.toScreenLength(radius);
        int x = Math.max(0, (int) Math.floor((viewport.toScreenX(bounds.minX()) - r) * pixelScaleX + pixelOriginX));
        int y = Math.max(0, (int) Math.floor((viewport.toScreenY(bounds.maxY()) - r) * pixelScaleY + pixelOriginY));
        int right = Math.min(width, (int) Math.ceil((viewport.toScreenX(bounds.maxX()) + r) * pixelScaleX + pixelOriginX));
        int top = Math.min(height, (int) Math.ceil((viewport.toScreenY(bounds.minY()) + r) * pixelScaleY + pixelOriginY));
        if (right <= x || top <= y) return;
        boolean scissorEnabled = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
        scissorState.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, scissorState);
        if (buffer == null || buffer.getWidth() != width || buffer.getHeight() != height) {
            if (buffer != null) buffer.dispose();
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, true);
            region = new TextureRegion(buffer.getColorBufferTexture());
        }
        try {
            buffer.begin();
            Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glScissor(x, y, right - x, top - y);
            // This offscreen pass resolves distance, not alpha. Only its final image is blended.
            Gdx.gl.glDisable(GL20.GL_BLEND);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDepthFunc(GL20.GL_LESS);
            Gdx.gl.glClearDepthf(1);
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            shader.bind();
            shader.setUniformMatrix("u_projection", projection);
            shader.setUniformf("u_viewport", viewport.left(), viewport.bottom(), viewport.scale());
            shader.setUniformf("u_radius", (float) radius);
            shader.setUniformf("u_limit", (float) geometry.limit(snake));
            shader.setUniformf("u_border", border);
            shader.setUniformf("u_outer", LegacySliderColour.outer(track));
            shader.setUniformf("u_inner", LegacySliderColour.inner(track));
            shader.setUniformf("u_shadowPortion", LegacySliderColour.SHADOW_PORTION);
            shader.setUniformf("u_borderPortion", LegacySliderColour.BORDER_PORTION);
            mesh.render(shader, GL20.GL_TRIANGLES, 0, count);
        } finally {
            // FrameBuffer.end() binds the default framebuffer; explicitly restore nested harness targets.
            buffer.end();
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previousBuffer);
            Gdx.gl.glViewport(viewportState.get(0), viewportState.get(1), width, height);
            Gdx.gl.glScissor(scissorState.get(0), scissorState.get(1), scissorState.get(2), scissorState.get(3));
            if (!scissorEnabled) Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        region.setRegion(x, y, right - x, top - y);
        region.flip(false, true);
        batch.setColor(1, 1, 1, alpha);
        batch.begin();
        batch.draw(region, (x - pixelOriginX) / pixelScaleX, (y - pixelOriginY) / pixelScaleY,
                (right - x) / pixelScaleX, (top - y) / pixelScaleY);
        batch.end();
        batch.setColor(Color.WHITE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override public void dispose() {
        if (buffer != null) buffer.dispose();
        if (shader != null) shader.dispose();
        buffer = null;
        shader = null;
    }
}
