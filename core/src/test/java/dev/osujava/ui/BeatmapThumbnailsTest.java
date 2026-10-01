package dev.osujava.ui;

import com.badlogic.gdx.graphics.Texture;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeatmapThumbnailsTest {
    private static final class Image extends Texture {
        int disposals;
        Image() { super(); }
        @Override public void dispose() { disposals++; }
    }
    @Test void frameSnapshotsCannotBeEvictedWhilePreparingOtherVisibleRows() {
        var loaded = new HashMap<Path, Image>();
        var cache = new BeatmapThumbnails(path -> { var image = new Image(); loaded.put(path, image); return image; });
        var visible = new LinkedHashSet<Path>();
        for (int i = 0; i < 24; i++) visible.add(Path.of("image" + i));
        cache.prepare(visible);
        for (var path : visible) {
            assertSame(loaded.get(path), cache.resident(path));
            assertEquals(0, loaded.get(path).disposals);
        }
        cache.prepare(Set.of(Path.of("image23")));
        assertEquals(6, loaded.values().stream().filter(image -> image.disposals == 1).count());
        assertEquals(0, loaded.get(Path.of("image23")).disposals);
        cache.close(); cache.close();
        assertTrue(loaded.values().stream().allMatch(image -> image.disposals == 1));
    }
    @Test void residentReadsNeverDecodeAndMissingFilesAreCached() {
        int[] loads = {0};
        var cache = new BeatmapThumbnails(path -> { loads[0]++; return null; });
        var path = Path.of("missing");
        assertNull(cache.resident(path)); assertEquals(0, loads[0]);
        cache.prepare(Set.of(path)); cache.prepare(Set.of(path));
        assertEquals(1, loads[0]);
        cache.close();
    }
    @Test void sharedTextureDoesNotShareRowFadeState() {
        var path = Path.of("image");
        var cache = new BeatmapThumbnails(ignored -> new Image());
        cache.prepare(Set.of(path));
        var first = new SongSelectForegroundAnimation();
        var second = new SongSelectForegroundAnimation();
        first.update(3, true, 1000, 16); second.update(1, true, 1000, 16);
        first.thumbnailLoaded(1000, 0);
        first.update(3, false, 1200, 0);
        second.thumbnailLoaded(1200, 0);
        assertEquals(.5f, first.snapshot().thumbnailOpacity());
        assertEquals(0, second.snapshot().thumbnailOpacity());
        assertEquals(255, first.snapshot().thumbnailBrightness());
        assertEquals(50, second.snapshot().thumbnailBrightness());
        assertSame(cache.resident(path), cache.get(path));
        cache.close();
    }

}
