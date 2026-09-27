package dev.osujava.skin;

import java.io.StringReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SongSelectConfigurationTest {
    @ParameterizedTest @ValueSource(strings = {"1.0", "2.1", "2.2", "latest"})
    void coloursAreIndependentOfVersion(String version) throws Exception {
        var config = SkinConfiguration.parse(new StringReader("[General]\nVersion: " + version
                + "\n[Colours]\nsongselectactivetext: 12, 34, 56 // comment\nSongSelectInactiveText=78,90,123\n"));
        assertEquals(version.equals("latest") ? 2.7 : Double.parseDouble(version), config.legacyVersion());
        assertEquals(new SkinConfiguration.Rgb(12 / 255f,34 / 255f,56 / 255f), config.songSelect().activeText());
        assertEquals(new SkinConfiguration.Rgb(78 / 255f,90 / 255f,123 / 255f), config.songSelect().inactiveText());
    }

    @ParameterizedTest @ValueSource(strings = {"", "1,2", "1,2,3,4", "256,0,0", "-1,0,0", "NaN,0,0", "red", "1.5,0,0"})
    void malformedColoursKeepDefaultOrPreviousValue(String value) throws Exception {
        var absent = SkinConfiguration.parse(new StringReader("[Colours]\nSongSelectActiveText: " + value + "\nSongSelectInactiveText: " + value));
        assertNull(absent.songSelect().activeText()); assertNull(absent.songSelect().inactiveText());
        var previous = SkinConfiguration.parse(new StringReader("[Colours]\nSongSelectActiveText: 1,2,3\nSongSelectInactiveText: 4,5,6\nSongSelectActiveText: " + value + "\nSongSelectInactiveText: " + value));
        assertEquals(1 / 255f, previous.songSelect().activeText().r());
        assertEquals(4 / 255f, previous.songSelect().inactiveText().r());
    }

    @Test void coloursInOtherSectionsAreIgnored() throws Exception {
        var config = SkinConfiguration.parse(new StringReader("[General]\nSongSelectActiveText: 1,2,3\n[Fonts]\nSongSelectInactiveText: 4,5,6"));
        assertEquals(SkinConfiguration.SongSelect.defaults(), config.songSelect());
        assertEquals(SkinConfiguration.SongSelect.defaults(), SkinConfiguration.defaults().songSelect());
    }
}
