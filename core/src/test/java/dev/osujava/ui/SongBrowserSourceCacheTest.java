package dev.osujava.ui;

import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SongBrowserSourceCacheTest {
    private BeatmapDifficulty chart(String title, String artist, double bpm, String path) throws Exception {
        String source="osu file format v14\n[Metadata]\nTitle:"+title+"\nArtist:"+artist+"\nCreator:Mapper\nVersion:Normal\n"
                +"[TimingPoints]\n0,"+(60000/bpm)+",4,0,0,100,1,0\n[HitObjects]\n100,100,1000,1,0\n";
        return new BeatmapFileParser().parse(source,"source.osu").difficulty().withAssets(null,null,Path.of(path));
    }
    private BeatmapSet set(String id, BeatmapDifficulty chart) {
        return new BeatmapSet(id,chart.title(),chart.artist(),chart.creator(),null,null,List.of(chart),List.of());
    }

    @Test void replacingTheSourceAtTheSamePathRefreshesMetadataBpmSearchAndSelectedRow() throws Exception {
        var original=chart("e\u0301clair", "Alpha",90,"edited.osu");
        var stable=chart("Beta", "Beta",120,"stable.osu");
        var unchanged=set("stable",stable);
        var model=new SongBrowserModel(List.of(set("edited",original),unchanged));
        model.select("edited",0); model.group(SongBrowserModel.Group.ARTIST);
        var selected=model.row(model.selectedKey());
        assertEquals("A",selected.parent.label);
        model.search("title=éclair"); assertEquals(List.of("edited"),model.visibleSets().stream().map(BeatmapSet::id).toList());
        model.search("");
        var revised=chart("Zèbre", "Zebra",240,"edited.osu");
        model.library(List.of(set("edited",revised),unchanged));
        assertSame(selected,model.row(model.selectedKey()));
        assertSame(revised,selected.difficulty); assertEquals("Z",selected.parent.label);
        assertTrue(model.rows().stream().noneMatch(row -> row.group() && row.label.equals("A")));
        model.search("title=éclair"); assertTrue(model.visibleSets().isEmpty());
        model.search("title=zèbre artist=zebra"); assertSame(revised,model.selectedDifficulty());
        model.search(""); model.group(SongBrowserModel.Group.BPM); model.sort(SongBrowserModel.Sort.BPM);
        assertEquals("240–<300 BPM",model.row(model.selectedKey()).parent.label);
        assertEquals(List.of("stable","edited"),model.visibleSets().stream().map(BeatmapSet::id).toList());
    }

    @Test void equalSetSnapshotsKeepTheExistingUniqueProjectionThroughRatingRebuilds() throws Exception {
        var chart=chart("Equal", "Artist",120,"equal.osu");
        var original=set("equal",chart); var equal=set("equal",chart);
        assertNotSame(original,equal); assertEquals(original,equal);
        var model=new SongBrowserModel(List.of(original,equal));
        model.ratings(d -> OptionalDouble.of(4)); model.sort(SongBrowserModel.Sort.DIFFICULTY);
        assertEquals(1,model.visibleSets().size()); assertSame(original,model.visibleSets().getFirst());
        model.group(SongBrowserModel.Group.DIFFICULTY); model.ratingsChanged();
        assertEquals(1,model.visibleSets().size()); assertSame(original,model.visibleSets().getFirst());
        assertEquals("4–<5 stars",model.row(model.selectedKey()).parent.label);
    }

    @Test void retainedSourcesRecomputeDateBucketsWhenTheLocalDayChanges() throws Exception {
        var chart=chart("Played", "Artist",120,"played.osu");
        var model=new SongBrowserModel(List.of(set("played",chart)));
        var facts=Map.of(chart,new SongBrowserActivity.Facts(Instant.parse("2026-10-03T12:00:00Z").toEpochMilli(),null));
        model.activity(facts,Clock.fixed(Instant.parse("2026-10-03T14:00:00Z"),ZoneOffset.UTC));
        model.group(SongBrowserModel.Group.RECENT);
        assertEquals("Today",model.row(model.selectedKey()).parent.label);
        model.activity(facts,Clock.fixed(Instant.parse("2026-10-04T14:00:00Z"),ZoneOffset.UTC));
        assertEquals("Yesterday",model.row(model.selectedKey()).parent.label);
    }
}
