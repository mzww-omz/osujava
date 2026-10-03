package dev.osujava.beatmap.parse;

import dev.osujava.beatmap.BeatmapFile;
import dev.osujava.beatmap.BeatmapParseIssues;
import dev.osujava.beatmap.BreakPeriod;
import dev.osujava.beatmap.HitObject;
import dev.osujava.beatmap.SliderData;
import dev.osujava.beatmap.SpinnerData;
import dev.osujava.beatmap.TimingPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BeatmapFileParserTest {
    @Test
    void countsBothShortAndRejectedObjectsWithoutCountingCommentsOrLosingValidRows() throws Exception {
        var chart = parser.parse("""
                osu file format v14
                [HitObjects]
                // comment is not an object

                200,100,2000,1,0
                damaged
                100,100,bad,1,0
                100,100,1000,2,0,B|bad,1,100
                100,100,1000,8,0
                100,100,1000,128,0,NaN
                100,100,1000,1,bad
                100,100,1000,1,0
                """, "partial.osu").difficulty();
        assertEquals(6, chart.skippedHitObjectCount());
        assertEquals(List.of(1000L, 2000L), chart.hitObjects().stream().map(HitObject::timeMs).toList());
        assertEquals(6, chart.withAssets(null, null).skippedHitObjectCount());
        assertEquals(6, chart.withAssets(null, null, Path.of("moved.osu")).skippedHitObjectCount());
        assertEquals(0, parser.parse("osu file format v14\n[HitObjects]\n// empty\n", "empty.osu").difficulty().skippedHitObjectCount());
    }
    @ParameterizedTest @ValueSource(strings={"bad", "", "NaN", "Infinity", "-Infinity", "1e999"})
    void retainsInvalidExplicitSettingsEvenWhenTolerantParsingUsesDefaults(String value) throws Exception {
        var chart=parser.parse("osu file format v14\n[Difficulty]\nCircleSize:"+value+"\n[HitObjects]\n100,100,1000,1,0", "settings.osu").difficulty();
        var expected=new BeatmapParseIssues(1,0,0);
        assertEquals(expected,chart.parseIssues());
        assertEquals(expected,chart.withAssets(null,null).parseIssues());
        assertEquals(expected,chart.withAssets(null,null,Path.of("moved.osu")).parseIssues());
        assertEquals(1,chart.hitObjects().size());
        if(value.equals("bad") || value.isEmpty()) assertEquals(5,chart.settings().circleSize());
    }
    @Test void missingDefaultsAndLegacyShortTimingRemainDifferentFromDamagedExplicitValues() throws Exception {
        var valid=parser.parse("osu file format v14\n[Difficulty]\nOverallDifficulty:7\n[TimingPoints]\n-100,500\n0,-50,4,0,0,100,0,0", "valid.osu").difficulty();
        assertEquals(BeatmapParseIssues.NONE,valid.parseIssues());assertEquals(7,valid.settings().approachRate());
        var bad=parser.parse("""
                osu file format v14
                [General]
                Mode:invalid
                StackLeniency:
                [Difficulty]
                HPDrainRate:NaN
                CircleSize:bad
                OverallDifficulty:Infinity
                ApproachRate:
                SliderMultiplier:1e999
                SliderTickRate:bad
                [TimingPoints]
                // absent optional fields are valid
                0,500
                short
                ,500
                1000,nope,4,0,0,100,1,0
                2000,500,broken,0,0,100,1,0
                3000,500,4,0,0,100,what,0
                ""","bad.osu").difficulty();
        assertEquals(new BeatmapParseIssues(8,5,0),bad.parseIssues());
        assertEquals(0,bad.mode());assertEquals(5,bad.timingPoints().size());
        assertThrows(IllegalArgumentException.class,()->new BeatmapParseIssues(-1,0,0));
    }
    @Test void inheritedNaNIsAnIntentionalTimingEncodingWhileNonFiniteTimesAndRedNaNAreIssues() throws Exception {
        var green=parser.parse("osu file format v14\n[TimingPoints]\n0,500\n1000,NaN,4,0,0,100,0,0", "green.osu").difficulty();
        assertEquals(BeatmapParseIssues.NONE,green.parseIssues());
        var bad=parser.parse("osu file format v14\n[TimingPoints]\nNaN,500\n0,Infinity\n1,NaN,4,0,0,100,1,0", "bad.osu").difficulty();
        assertEquals(new BeatmapParseIssues(0,3,0),bad.parseIssues());
    }
    @Test void reportsOnlyKeptSpinnerEndCorrectionsAndPreservesExistingClamp() throws Exception {
        var chart=parser.parse("osu file format v14\n[HitObjects]\n256,192,1000,8,0,500\n256,192,2000,8,bad,500\n256,192,3000,8,0,3000", "spinner.osu").difficulty();
        assertEquals(new BeatmapParseIssues(0,0,1),chart.parseIssues());assertEquals(1,chart.skippedHitObjectCount());
        assertEquals(2,chart.hitObjects().size());assertEquals(1000,chart.hitObjects().getFirst().endTimeMs());
    }
    private final BeatmapFileParser parser = new BeatmapFileParser();

    @Test void additionalMetadataSurvivesBothAssetResolutionPathsAndDefaultsSafely() throws Exception {
        var chart = parser.parse("""
                osu file format v14
                [Metadata]
                TitleUnicode: 夜空
                ArtistUnicode: 星野
                Source: Moon Game
                Tags: piano instrumental
                BeatmapID: 123
                BeatmapSetID: 456
                """, "metadata.osu").difficulty();
        var expected = new dev.osujava.beatmap.BeatmapMetadata("夜空","星野","Moon Game","piano instrumental",123,456);
        assertEquals(expected, chart.metadata());
        assertEquals(expected, chart.withAssets(null,null).metadata());
        assertEquals(expected, chart.withAssets(null,null,null).metadata());
        assertEquals(dev.osujava.beatmap.BeatmapMetadata.EMPTY,
                parser.parse("osu file format v14\n[Metadata]\nBeatmapID: invalid\nBeatmapSetID: invalid", "empty.osu").difficulty().metadata());
    }

    @Test
    void keepsBreaksLeadInAndOriginalByteDigestsAcrossAssetResolution(@TempDir Path dir) throws Exception {
        String source = "\uFEFFosu file format v14\r\n[General]\r\nAudioLeadIn: 750\r\n[Events]\r\n"
                + "2,3000,4000\r\nBreak,1000,2000\r\n";
        Path path = dir.resolve("map.osu");
        Files.writeString(path, source);
        var difficulty = parser.parse(path).difficulty();
        var data = difficulty.playData();
        assertEquals(List.of(new BreakPeriod(1000, 2000), new BreakPeriod(3000, 4000)), data.breaks());
        assertEquals(750, data.audioLeadInMs());
        assertEquals("1862102c0dbefe2031383a7226556e88ffa7b8fc0e1cd4bec80d3c067778d865", data.sha256());
        assertEquals("04f080035ff4c03cbba6eede5d103354", data.md5());
        assertEquals(data, difficulty.withAssets(null, null).playData());
        assertEquals(data, difficulty.withAssets(null, null, path).playData());
        assertThrows(UnsupportedOperationException.class, () -> data.breaks().clear());
        Files.writeString(path, source.replace("\r\n", "\n"));
        assertNotEquals(data.sha256(), parser.parse(path).difficulty().playData().sha256());
        assertNotEquals(data.md5(), parser.parse(path).difficulty().playData().md5());
    }

    @Test
    void preservesOverlappingAndOutOfObjectRangeBreaksForRulesetInterpretation() throws Exception {
        var data = parser.parse("osu file format v7\n[Events]\n2,20,100\nBreak,10,50\n2,20,100\n2,200,200",
                "breaks.osu").difficulty().playData();
        assertEquals(List.of(new BreakPeriod(10, 50), new BreakPeriod(20, 100),
                new BreakPeriod(20, 100), new BreakPeriod(200, 200)), data.breaks());
        assertEquals(0, data.audioLeadInMs());
    }

    @Test
    void damagedNewFieldsProduceParseErrorsInsteadOfSilentlyChangingDrain() {
        for (String event : List.of("2,20,10", "Break,0,NaN", "2,0,Infinity", "2,0", "2,0,2147483648")) {
            assertThrows(BeatmapParseException.class, () -> parser.parse("osu file format v14\n[Events]\n" + event, "bad.osu"));
        }
        for (String value : List.of("invalid", "-1", "2147483648")) {
            assertThrows(BeatmapParseException.class, () -> parser.parse("osu file format v14\n[General]\nAudioLeadIn:" + value, "bad.osu"));
        }
    }

    @Test
    void previewTimeSurvivesAssetResolutionAndDefaultsSafely() throws Exception {
        for (String value : new String[]{"12345", "-1", "invalid", "-900"}) {
            var chart = parser.parse("osu file format v14\n[General]\nPreviewTime: " + value, "preview.osu").difficulty();
            int expected = value.equals("12345") ? 12345 : -1;
            assertEquals(expected, chart.previewTimeMs());
            assertEquals(expected, chart.withAssets(null, null).previewTimeMs());
            assertEquals(expected, chart.withAssets(null, null, null).previewTimeMs());
        }
    }

    @Test
    void parsesMetadataSettingsEventsTimingAndObjects() throws Exception {
        BeatmapFile file = parser.parse("""
                osu file format v14
                [General]
                AudioFilename: song name.ogg
                Mode: 0
                [Metadata]
                Title: Sakura
                TitleUnicode: 桜
                Artist: Artist
                ArtistUnicode: 作曲者
                Creator: Mapper
                Version: Hard
                [Difficulty]
                HPDrainRate: 6
                CircleSize: 4
                OverallDifficulty: 7
                ApproachRate: 8
                SliderMultiplier: 1.6
                SliderTickRate: 2
                [Events]
                //Background and video events
                0,0,"背景,夜景.png",0,0
                [TimingPoints]
                1000,500,4,1,0,80,1,0
                1200,-50,4,0,0,100,0,0
                [HitObjects]
                64,96,1500,1,0
                128,192,2000,2,2,B|200:192|300:256,3,280,0|0|0,0:0|0:0|0:0,0:0:0:0:
                256,192,2500,8,0,3000
                """, "test.osu");

        assertEquals(14, file.formatVersion());
        assertEquals("桜", file.displayTitle());
        assertEquals("作曲者", file.displayArtist());
        assertEquals("song name.ogg", file.difficulty().audioFilename());
        assertEquals("背景,夜景.png", file.difficulty().backgroundFilename());
        assertEquals(0, file.difficulty().mode());
        assertEquals(8, file.difficulty().settings().approachRate());
        assertEquals(1.6, file.difficulty().settings().sliderMultiplier());
        assertEquals(2, file.difficulty().timingPoints().size());

        TimingPoint point = file.difficulty().timingPoints().getFirst();
        assertEquals(1000, point.timeMs());
        assertEquals(500, point.beatLength());
        assertEquals(80, point.volume());
        assertEquals(false, file.difficulty().timingPoints().get(1).uninherited());
        assertEquals(-50, file.difficulty().timingPoints().get(1).beatLength());
        assertEquals(3, file.difficulty().hitObjects().size());
        assertEquals(HitObject.Type.CIRCLE, file.difficulty().hitObjects().getFirst().type());
        assertEquals(64, file.difficulty().hitObjects().getFirst().x());
        assertEquals(HitObject.Type.SLIDER, file.difficulty().hitObjects().get(1).type());
        HitObject slider = file.difficulty().hitObjects().get(1);
        assertEquals(128, slider.x());
        assertEquals(192, slider.y());
        assertEquals(2000, slider.timeMs());
        assertEquals(SliderData.CurveType.BEZIER, slider.sliderData().curveType());
        assertEquals(3, slider.sliderData().controlPoints().size());
        assertEquals(2, slider.sliderData().repeatCount());
        assertEquals(280, slider.sliderData().pixelLength());
        assertEquals(HitObject.Type.SPINNER, file.difficulty().hitObjects().get(2).type());
        HitObject spinner = file.difficulty().hitObjects().get(2);
        assertEquals(2500, spinner.timeMs());
        assertEquals(3000, spinner.endTimeMs());
        assertEquals(500, spinner.durationMs());
        assertEquals(new SpinnerData(3000), spinner.spinnerData());
    }

    @Test
    void preservesUnsupportedModeAndUsesDefaultsForOptionalSections() throws Exception {
        BeatmapFile file = parser.parse("""
                osu file format v3
                [General]
                Mode: 3
                [Metadata]
                Title: Test
                Artist: Local
                Creator: Mapper
                Version: Mania
                [HitObjects]
                1,2,3,1,0
                """, "mania.osu");

        assertEquals(3, file.difficulty().mode());
        assertEquals(5, file.difficulty().settings().overallDifficulty());
        assertNull(file.difficulty().audioPath());
        assertEquals("Test", file.displayTitle());
    }

    @Test
    void readsBeatmapSetIdForStableLocalIdentity() throws Exception {
        BeatmapFile file = parser.parse("""
                osu file format v14
                [Metadata]
                Title: Test
                BeatmapSetID: 1842
                [HitObjects]
                1,2,3,1,0
                """, "set-id.osu");

        assertEquals(1842, file.beatmapSetId());
    }

    @Test
    void parsesExplicitSliderCurveSegmentsAndDegreeSpecificBSpline() throws Exception {
        BeatmapFile file = parser.parse("""
                osu file format v14
                [Difficulty]
                SliderMultiplier: 1.4
                [HitObjects]
                256,192,1000,2,0,L|300:192|B2|350:192|400:220,2,150
                """, "segments.osu");

        SliderData slider = file.difficulty().hitObjects().getFirst().sliderData();
        assertEquals(2, slider.segments().size());
        assertEquals(SliderData.CurveType.LINEAR, slider.segments().get(0).curveType());
        assertEquals(SliderData.CurveType.BSPLINE, slider.segments().get(1).curveType());
        assertEquals(2, slider.segments().get(1).degree());
        assertEquals(1, slider.repeatCount());
    }

    @Test
    void allowsLegacySliderLinesWithoutAnExpectedPixelLength() throws Exception {
        BeatmapFile file = parser.parse("""
                osu file format v3
                [HitObjects]
                10,20,30,2,0,L|40:50,1
                """, "missing-length.osu");

        assertEquals(0, file.difficulty().hitObjects().getFirst().sliderData().pixelLength());
    }

    @Test
    void parsesSpinnerEndTimeAndUsesPlayfieldCenter() throws Exception {
        BeatmapFile file = parser.parse("""
                osu file format v14
                [HitObjects]
                80,120,1234,12,0,2234.5
                200,200,3000,8,0
                """, "spinner.osu");

        HitObject spinner = file.difficulty().hitObjects().getFirst();
        assertEquals(HitObject.Type.SPINNER, spinner.type());
        assertEquals(256, spinner.x());
        assertEquals(192, spinner.y());
        assertEquals(1234, spinner.timeMs());
        assertEquals(2234.5, spinner.endTimeMs());
        assertEquals(1000.5, spinner.durationMs());
        assertEquals(1, file.difficulty().hitObjects().size(), "A spinner without an end time is skipped");
    }

    @Test
    void rejectsFilesWithoutOsuHeader() {
        assertThrows(BeatmapParseException.class, () -> parser.parse("[General]", "bad.osu"));
    }
}
