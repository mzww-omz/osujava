package dev.osujava.ui;

import dev.osujava.beatmap.*;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.score.*;
import java.time.*;
import java.util.*;

/** Revision/day-driven local projection. Renderer and search never read storage. */
final class SongBrowserActivity {
    record Facts(Long lastPlayedAt, Long addedAt) {
        static final Facts UNKNOWN = new Facts(null,null);
        double daysSincePlayed(long now) { return Math.max(0,now-lastPlayedAt)/86_400_000.0; }
    }
    private final BeatmapLibrary library;
    private final LocalPlayHistory history;
    private final LocalScoreStore scores;
    private final Clock clock;
    private long libraryRevision=-1, historyRevision=-1, scoreRevision=-1;
    private long dayStart=Long.MAX_VALUE, dayEnd=Long.MIN_VALUE;
    private Map<BeatmapDifficulty,Facts> facts=Map.of();
    SongBrowserActivity(BeatmapLibrary library, LocalPlayHistory history, LocalScoreStore scores, Clock clock) {
        this.library=library; this.history=history; this.scores=scores; this.clock=clock;
    }
    Map<BeatmapDifficulty,Facts> facts() { return facts; }
    boolean refresh() {
        long now=clock.millis();
        if (libraryRevision==library.revision() && historyRevision==history.revision() && scoreRevision==scores.revision()
                && now>=dayStart && now<dayEnd) return false;
        var today=LocalDate.now(clock);
        var contentFacts=new HashMap<BeatmapContentKey,Facts>(); var next=new IdentityHashMap<BeatmapDifficulty,Facts>();
        for(var set:library.all()) for(var diff:set.difficulties()) {
            var key=BeatmapContentKey.of(diff);
            var value=key==null ? Facts.UNKNOWN : contentFacts.computeIfAbsent(key,content -> {
                Long last=history.lastPlayed(content);
                // Only the saved timestamp can be recovered from old scores; no attempt is invented.
                if(last==null) for(var score:scores.query(content)) if(last==null || score.playedAt()>last) last=score.playedAt();
                return new Facts(last,library.addedAt(content));
            });
            next.put(diff,value);
        }
        facts=Collections.unmodifiableMap(next);
        libraryRevision=library.revision(); historyRevision=history.revision(); scoreRevision=scores.revision();
        dayStart=today.atStartOfDay(clock.getZone()).toInstant().toEpochMilli();
        dayEnd=today.plusDays(1).atStartOfDay(clock.getZone()).toInstant().toEpochMilli();
        return true;
    }
}
