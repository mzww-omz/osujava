package dev.osujava.ui;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Pixmap;
import java.util.function.Supplier;

/** Owns a transparent native cursor. No pointer capture/warping; restore on hide and active dispose. */
final class GameplayCursorVisibility implements AutoCloseable {
    private final Graphics graphics;
    private final Supplier<Cursor> factory;
    private Cursor hidden;
    private boolean active;
    GameplayCursorVisibility(Graphics graphics) {
        this(graphics, () -> {
            Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            try {
                pixmap.setColor(0, 0, 0, 0); pixmap.fill();
                return graphics.newCursor(pixmap, 0, 0);
            } finally { pixmap.dispose(); }
        });
    }
    GameplayCursorVisibility(Graphics graphics, Supplier<Cursor> factory) {
        this.graphics = graphics; this.factory = factory;
    }
    void show() {
        if (hidden == null) hidden = factory.get();
        graphics.setCursor(hidden); active = true;
    }
    void hide() {
        if (!active) return;
        graphics.setSystemCursor(Cursor.SystemCursor.Arrow); active = false;
    }
    @Override public void close() {
        hide();
        if (hidden != null) { hidden.dispose(); hidden = null; }
    }
}
