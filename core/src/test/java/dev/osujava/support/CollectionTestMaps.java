package dev.osujava.support;

import dev.osujava.beatmap.*;
import dev.osujava.beatmap.parse.BeatmapFileParser;
import java.nio.file.Path;
import java.util.*;

public final class CollectionTestMaps {
    public static BeatmapSet set(String id,int count) throws Exception {
        var diffs=new ArrayList<BeatmapDifficulty>();
        for(int i=0;i<count;i++) diffs.add(new BeatmapFileParser().parse("osu file format v14\n[Metadata]\nTitle:"+id+"\nArtist:A\nCreator:C\nVersion:D"+i+"\n[HitObjects]\n"+(100+i)+",100,1000,1,0",id+i+".osu").difficulty().withAssets(null,null,Path.of(id,"D"+i+".osu")));
        return new BeatmapSet(id,id,"A","C",null,null,diffs,List.of());
    }
    private CollectionTestMaps() { }
}
