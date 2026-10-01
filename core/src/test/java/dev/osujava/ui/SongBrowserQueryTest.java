package dev.osujava.ui;

import dev.osujava.beatmap.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserQueryTest {
    static BeatmapDifficulty difficulty(String version, int mode, double ar) {
        return new BeatmapDifficulty("Evening Sky", "Hoshino", "Guest Mapper", version, mode, "", "",
                new DifficultySettings(6, 4, 8.25, ar, 1.4, 1), List.of(), List.of(), null, null, null, -1,
                new BeatmapMetadata("夜空", "星野", "Moon Game", "instrumental piano café", 123, 456));
    }

    private boolean matches(String query) {
        return new SongBrowserQuery(query).matches(SongBrowserQuery.Document.of(difficulty("Very Hard",0,9.15)));
    }

    @ParameterizedTest @ValueSource(strings={"evening", "HOSHINO", "guest", "very", "夜空", "星野", "moon", "piano",
            "CAFE\u0301", "piano 夜空 guest", "123", "456", "123 piano", "", "   "})
    void plainSearchIncludesAllLocalMetadataAndPositiveIds(String query) { assertTrue(matches(query)); }

    @ParameterizedTest @ValueSource(strings={"evening missing", "789", "1234", "title:evening", "source=moon", "tags=piano",
            "title=夜空", "difficulty=easy", "\"evening sky\"", "piano\tguest", "piano\u3000guest"})
    void unsupportedFieldsAndPunctuationRemainLiteralAndTitleFieldIsRomanised(String query) { assertFalse(matches(query)); }

    @ParameterizedTest @ValueSource(strings={"title=EVENING", "title==sky", "artist=星野", "artist=hoshi", "creator=guest",
            "difficulty=hard", "difficulty=\"very hard\"", "creator=\"guest mapper\"", "title!=morning", "title<absent",
            "title>=absent", "title=", "difficulty!=easy"})
    void textFieldsUseSubstringMatchingAndOtherOperatorsInvertContains(String query) { assertTrue(matches(query)); }

    @ParameterizedTest @ValueSource(strings={"ar=9.1", "ar==9.1", "ar!=9.2", "ar<9.2", "ar>9", "ar<=9.1", "ar>=9.1",
            "cs=4", "hp=6", "od=8.2", "ar>9 hp<7 cs==4 od!=8.3"})
    void settingsRoundNativeFloatToOneDecimalWithTiesToEven(String query) { assertTrue(matches(query)); }

    @ParameterizedTest @ValueSource(strings={"ar=9.15", "ar=9.2", "ar<9.1", "ar>9.1", "ar!=9.1", "od=8.3",
            "ar=", "ar=NaN", "ar=Infinity", "ar=word", "ar==="})
    void invalidAndNonMatchingNumericComparisonsDoNotMatch(String query) { assertFalse(matches(query)); }

    @ParameterizedTest @ValueSource(ints={0,1,2,3})
    void arAndCsExcludeTaikoAndManiaEvenForNotEqual(int mode) {
        var doc = SongBrowserQuery.Document.of(difficulty("Hard",mode,9));
        for (String query : List.of("ar=9", "ar!=8", "cs=4", "cs!=3"))
            assertEquals(mode == 0 || mode == 2, new SongBrowserQuery(query).matches(doc));
        assertTrue(new SongBrowserQuery("od=8.2 hp=6").matches(doc));
    }

    @ParameterizedTest @CsvSource({"osu,0", "o,0", "os,0", "taiko,1", "t,1", "tai,1",
            "catch,2", "c,2", "cat,2", "mania,3", "m,3", "MAN,3"})
    void modeNamesAndPrefixesMatchOnlyTheirMode(String name, int expected) {
        for (int mode=0; mode<4; mode++) {
            var doc = SongBrowserQuery.Document.of(difficulty("Hard",mode,9));
            assertEquals(mode == expected,new SongBrowserQuery("mode="+name).matches(doc));
            assertEquals(mode != expected,new SongBrowserQuery("mode!="+name).matches(doc));
        }
    }

    @ParameterizedTest @ValueSource(strings={"mode==osu", "mode<taiko", "mode<=o", "mode>=osu", "mode!=mania",
            "mode!=", "mode>unknown", "mode>=unknown", "mode!=0"})
    void modeComparisonUsesEnumOrderingIncludingUnknownNameNaN(String query) { assertTrue(matches(query)); }

    @ParameterizedTest @ValueSource(strings={"mode=", "mode=unknown", "mode=0", "mode=osu!", "mode<unknown",
            "mode<=", "mode>osu", "mode!=o"})
    void modeUnknownNamesAreNotOrdinaryTextOrNumericModeIds(String query) { assertFalse(matches(query)); }

    @ParameterizedTest @ValueSource(strings={"bpm=120", "bpm!=240", "bpm>=120", "bpm<121", "length=66",
            "length==66", "length<67", "length<=66", "length>65", "length>=66", "length!=66.999",
            "drain=55", "drain>54.999", "bpm=120 length=66 drain=55 mode=o"})
    void tempoLengthAndDrainUseImportedStatistics(String query) throws Exception {
        var chart = new dev.osujava.beatmap.parse.BeatmapFileParser().parse("""
                osu file format v14
                [Events]
                2,10000,20000
                [TimingPoints]
                0,500,4,0,0,100,1,0
                50000,250,4,0,0,100,1,0
                [HitObjects]
                256,192,1000,1,0
                256,192,66999,1,0
                """, "query.osu").difficulty();
        assertTrue(new SongBrowserQuery(query).matches(SongBrowserQuery.Document.of(chart)));
        assertFalse(new SongBrowserQuery("bpm=240").matches(SongBrowserQuery.Document.of(chart)), "BPM search uses common tempo, not the maximum");
    }

    static Stream<Arguments> tokenCases() {
        return Stream.of(
                Arguments.of("title=\"one two\" extra", List.of("title=one two", "extra")),
                Arguments.of("artist!=\"one two\"", List.of("artist!=one two")),
                Arguments.of("title<=\"one two\"", List.of("title<=one two")),
                Arguments.of("artist>\"one two\"", List.of("artist>\"one", "two\"")),
                Arguments.of("\"one two\"", List.of("\"one", "two\"")),
                Arguments.of("title=\"one two", List.of("title=\"one", "two")),
                Arguments.of("title=\"one two\" artist=\"three", List.of("title=one two", "artist=\"three")),
                Arguments.of("title=\"a\"suffix", List.of("title=asuffix")),
                Arguments.of("title=\"\"", List.of("title=")),
                Arguments.of("  a   b  ", List.of("a", "b")),
                Arguments.of("a\tb\u3000c", List.of("a\tb\u3000c")));
    }
    @ParameterizedTest @MethodSource("tokenCases")
    void tokenizerFollowsStableQuoteBudgetAndAsciiSpaces(String query, List<String> tokens) {
        assertEquals(tokens, SongBrowserQuery.tokens(query));
    }

    @Test void numericTokenCanStillMatchTextAndMetadataDefaultsDoNotInventAnId() {
        assertTrue(new SongBrowserQuery("2026").matches(SongBrowserQuery.Document.of(difficulty("2026 mix",0,5))));
        var plain = new BeatmapDifficulty("Song","Artist","Mapper","Easy",0,"","",null,List.of(),List.of(),null,null);
        assertFalse(new SongBrowserQuery("123").matches(SongBrowserQuery.Document.of(plain)));
    }
}
