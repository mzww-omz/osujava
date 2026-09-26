package dev.osujava.ui;

import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Cursor;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class GameplayCursorVisibilityTest {
    @Test void hidesRestoresOnHideAndDisposesOnceWithoutOverridingNextScreensCursor() {
        var calls = new ArrayList<String>();
        Graphics graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
                (p, m, a) -> { calls.add(m.getName()); return null; });
        var visibility = new GameplayCursorVisibility(graphics, () -> () -> calls.add("dispose"));
        visibility.show(); visibility.hide();
        // Song Select/next Screen has now shown its own cursor: inactive dispose must leave it alone.
        visibility.close(); visibility.close();
        assertEquals(java.util.List.of("setCursor", "setSystemCursor", "dispose"), calls);
    }
    @Test void activeDisposeRestoresAndShowHideReuseTheNativeCursor() {
        var calls = new ArrayList<String>();
        Graphics graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class},
                (p, m, a) -> {
                    if (m.getName().equals("setSystemCursor")) assertEquals(Cursor.SystemCursor.Arrow, a[0]);
                    calls.add(m.getName()); return null;
                });
        var visibility = new GameplayCursorVisibility(graphics, () -> { calls.add("create"); return () -> calls.add("dispose"); });
        visibility.show(); visibility.hide(); visibility.show(); visibility.close();
        assertEquals(java.util.List.of("create", "setCursor", "setSystemCursor", "setCursor", "setSystemCursor", "dispose"), calls);
    }
}
