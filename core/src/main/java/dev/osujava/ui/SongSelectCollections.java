package dev.osujava.ui;

import com.badlogic.gdx.Input;
import dev.osujava.beatmap.*;
import dev.osujava.collection.LocalCollectionStore;
import dev.osujava.collection.LocalCollectionStore.Member;
import dev.osujava.library.BeatmapLibrary;
import dev.osujava.score.DifficultyIdentity;
import dev.osujava.ui.theme.UiLayout;
import dev.osujava.ui.SongSelectToolboxLayout.Bounds;
import java.util.*;

/** Collection dialog commands and cached presentation, without drawing or storage in Renderer. */
final class SongSelectCollections {
    enum Mode { CLOSED, OPTIONS, MANAGE, DELETE }
    enum Edit { NONE, CREATE, RENAME }
    enum Button { MANAGE, CLOSE, CREATE, RENAME, DELETE, DIFFICULTY, SET, MISSING, PREVIOUS, NEXT, SAVE, CANCEL, CONFIRM,
        DELETE_BEATMAP, MARK_PLAYED, CLEAR_SCORES, EDIT_BEATMAP }
    record Row(UUID id,String name,String count) { }
    record Snapshot(Mode mode,List<Row> rows,UUID selected,String selectedName,int page,Edit edit,String draft,String error,String target,
                    boolean hasDifficulty,boolean hasSet,boolean difficultyIncluded,boolean setIncluded,int missing) {
        boolean open() { return mode!=Mode.CLOSED; }
    }
    static final int PAGE_SIZE=8;
    private final LocalCollectionStore store;
    private final BeatmapLibrary library;
    private long revision=-1, libraryRevision=-1;
    private Mode mode=Mode.CLOSED;
    private Edit edit=Edit.NONE;
    private UUID selected;
    private int page;
    private String draft="", error="", target="";
    private char highSurrogate;
    private char suppressedTyped;
    private Member difficulty;
    private List<Member> set=List.of();
    private Set<BeatmapContentKey> available=Set.of();
    private List<Row> presentationRows=List.of();
    private Snapshot snapshot;
    private Snapshot closingSnapshot;
    final SongSelectMenuAnimation animation=new SongSelectMenuAnimation();
    SongSelectCollections(LocalCollectionStore store,BeatmapLibrary library) { this.store=store; this.library=library; refresh(true); }
    Snapshot snapshot() { return snapshot; }
    Snapshot presentation() { return !open() && animation.visible() && closingSnapshot!=null ? closingSnapshot : snapshot; }
    boolean open() { return mode!=Mode.CLOSED; }
    void options(BeatmapSet beatmapSet,BeatmapDifficulty diff) {
        difficulty=member(beatmapSet,diff); set=beatmapSet==null ? List.of() : beatmapSet.difficulties().stream().map(d -> member(beatmapSet,d)).filter(Objects::nonNull).toList();
        target=diff==null ? "No beatmap selected" : diff.artist()+" - "+diff.title()+" ["+diff.version()+"]";
        mode=Mode.OPTIONS; edit=Edit.NONE; error=store.error(); animation.open();closingSnapshot=null;refresh(true);
    }
    private static Member member(BeatmapSet set,BeatmapDifficulty diff) {
        var key=BeatmapContentKey.of(diff); var location=set==null ? null : DifficultyIdentity.of(set.id(),diff);
        return key==null || location==null ? null : new Member(key,location);
    }
    void close() { if(mode==Mode.OPTIONS) closingSnapshot=snapshot;animation.close(); mode=Mode.CLOSED; edit=Edit.NONE; highSurrogate=suppressedTyped=0; publish(); }
    void refresh() { refresh(false); }
    private void refresh(boolean forced) {
        if(!forced && revision==store.revision() && libraryRevision==library.revision()) return;
        if(!forced && open() && libraryRevision!=library.revision()) { close(); }
        var keys=new HashSet<BeatmapContentKey>();
        for(var s:library.all()) for(var d:s.difficulties()) { var key=BeatmapContentKey.of(d); if(key!=null) keys.add(key); }
        available=Set.copyOf(keys); revision=store.revision(); libraryRevision=library.revision();
        presentationRows=store.all().stream().map(c -> new Row(c.id(),c.name(),c.members().size()+" maps · "+missing(c)+" missing")).toList();
        if(store.find(selected)==null) selected=store.all().isEmpty() ? null : store.all().getFirst().id();
        page=Math.min(page,Math.max(0,(store.all().size()-1)/PAGE_SIZE)); publish();
    }
    private void publish() {
        var c=store.find(selected); var members=c==null ? Set.<BeatmapContentKey>of() : c.members().stream().map(Member::content).collect(java.util.stream.Collectors.toSet());
        snapshot=new Snapshot(mode,presentationRows,selected,c==null ? "" : c.name(),page,edit,draft,error,target,difficulty!=null,!set.isEmpty(),
                difficulty!=null && members.contains(difficulty.content()),!set.isEmpty() && set.stream().allMatch(m -> members.contains(m.content())),c==null ? 0 : missing(c));
    }
    private int missing(LocalCollectionStore.Collection c) { return (int)c.members().stream().filter(m -> !available.contains(m.content())).count(); }
    private void choose(int index) {
        if(store.all().isEmpty()) return;
        index=Math.max(0,Math.min(index,store.all().size()-1)); selected=store.all().get(index).id(); page=index/PAGE_SIZE; edit=Edit.NONE; error=""; publish();
    }
    void key(int key,boolean control) {
        highSurrogate=0;
        if(key==Input.Keys.ESCAPE) {
            if(edit!=Edit.NONE) { edit=Edit.NONE; highSurrogate=0; error=""; publish(); }
            else if(mode==Mode.DELETE) { mode=Mode.MANAGE; publish(); } else close();
            return;
        }
        if(edit!=Edit.NONE) {
            if(key==Input.Keys.ENTER) action(Button.SAVE);
            else if(key==Input.Keys.BACKSPACE && !draft.isEmpty()) { draft=draft.substring(0,draft.offsetByCodePoints(draft.length(),-1)); publish(); }
            else if(control && key==Input.Keys.A) { draft=""; publish(); }
            return;
        }
        if(mode==Mode.OPTIONS) { if(key==Input.Keys.NUM_1) action(Button.MANAGE); else if(key==Input.Keys.NUM_6) close(); return; }
        if(mode==Mode.DELETE) { if(key==Input.Keys.ENTER) action(Button.CONFIRM); return; }
        if(key==Input.Keys.N) { action(Button.CREATE); suppressedTyped='n'; } else if(key==Input.Keys.R) { action(Button.RENAME); suppressedTyped='r'; }
        else if(key==Input.Keys.DEL) action(Button.DELETE);
        else if(key==Input.Keys.UP || key==Input.Keys.DOWN) {
            int i=0; for(;i<store.all().size();i++) if(store.all().get(i).id().equals(selected)) break;
            choose(i+(key==Input.Keys.UP ? -1 : 1));
        }
    }
    void typed(char ch) {
        char suppressed=suppressedTyped; suppressedTyped=0;
        if(suppressed!=0 && Character.toLowerCase(ch)==suppressed) return;
        if(edit==Edit.NONE || Character.isISOControl(ch)) return;
        if(Character.isHighSurrogate(ch)) { highSurrogate=ch; return; }
        String append;
        if(Character.isLowSurrogate(ch)) { if(highSurrogate==0) return; append=new String(new char[]{highSurrogate,ch}); }
        else append=String.valueOf(ch);
        highSurrogate=0;
        if(draft.codePointCount(0,draft.length())<80) { draft+=append; publish(); }
    }
    void scroll(float amount) {
        if(mode==Mode.MANAGE && edit==Edit.NONE) { page=Math.max(0,Math.min(page+(amount>0 ? 1 : -1),Math.max(0,(store.all().size()-1)/PAGE_SIZE))); publish(); }
    }
    void click(UiLayout layout,float x,float y) {
        int index=0;
        for(var b:buttons(snapshot)) {
            boolean visible=mode!=Mode.OPTIONS || animation.rowAlpha(index)>0;
            index++;
            if(!visible) continue;
            var hit=interaction(layout,b);
            if(hit.contains(x,y)) { if(enabled(snapshot,b)) action(b); return; }
        }
        if(mode==Mode.MANAGE && edit==Edit.NONE) for(int i=0;i<PAGE_SIZE;i++) if(page*PAGE_SIZE+i<store.all().size() && rowBounds(layout,i).contains(x,y)) {
            choose(page*PAGE_SIZE+i); return;
        }
    }
    Bounds interaction(UiLayout layout,Button button) {
        return mode==Mode.OPTIONS ? animation.bounds(optionBounds(layout,button),buttons(snapshot).indexOf(button)) : bounds(layout,button);
    }
    static Bounds optionBounds(UiLayout layout,Button button) {
        int index=switch(button) {case MANAGE->0;case DELETE_BEATMAP->1;case MARK_PLAYED->2;case CLEAR_SCORES->3;case EDIT_BEATMAP->4;case CLOSE->5;default->throw new IllegalArgumentException("Not an Options command");};
        return new SongSelectSelectorLayout(layout).option(index);
    }
    void action(Button button) {
        if(!enabled(snapshot,button)) return;
        boolean changed=false; error="";
        switch(button) {
            case CLOSE -> { close(); return; }
            case MANAGE -> mode=Mode.MANAGE;
            case CREATE -> { edit=Edit.CREATE; draft=""; highSurrogate=suppressedTyped=0; }
            case RENAME -> { var c=store.find(selected); if(c!=null) { edit=Edit.RENAME; draft=c.name(); highSurrogate=suppressedTyped=0; } }
            case SAVE -> {
                if(edit==Edit.CREATE) { var c=store.create(draft); changed=c!=null; if(changed) selected=c.id(); }
                else if(edit==Edit.RENAME) changed=store.rename(selected,draft);
                if(changed) edit=Edit.NONE; else error=store.error();
            }
            case CANCEL -> { edit=Edit.NONE; mode=Mode.MANAGE; }
            case DELETE -> { if(selected!=null) mode=Mode.DELETE; }
            case CONFIRM -> { changed=store.delete(selected); if(changed) mode=Mode.MANAGE; else error=store.error(); }
            case DIFFICULTY -> {
                if(selected!=null && difficulty!=null) changed=snapshot.difficultyIncluded() ? store.remove(selected,Set.of(difficulty.content())) : store.add(selected,List.of(difficulty));
                if(!changed) error=store.error();
            }
            case SET -> {
                if(selected!=null && !set.isEmpty()) changed=snapshot.setIncluded() ? store.remove(selected,set.stream().map(Member::content).collect(java.util.stream.Collectors.toSet())) : store.add(selected,set);
                if(!changed) error=store.error();
            }
            case MISSING -> {
                var c=store.find(selected); if(c!=null) changed=store.remove(selected,c.members().stream().map(Member::content).filter(k -> !available.contains(k)).collect(java.util.stream.Collectors.toSet()));
                if(!changed) error=store.error();
            }
            case PREVIOUS -> page=Math.max(0,page-1);
            case NEXT -> page=Math.min(Math.max(0,(store.all().size()-1)/PAGE_SIZE),page+1);
            case DELETE_BEATMAP,MARK_PLAYED,CLEAR_SCORES,EDIT_BEATMAP -> { return; }
        }
        if(changed) {
            refresh(true);
            for(int i=0;i<store.all().size();i++) if(store.all().get(i).id().equals(selected)) { page=i/PAGE_SIZE; break; }
        }
        if(store.status()==LocalCollectionStore.Status.UNAVAILABLE && error.isEmpty()) error=store.error();
        publish();
    }
    static List<Button> buttons(Snapshot s) {
        if(s.mode()==Mode.OPTIONS) return List.of(Button.MANAGE,Button.DELETE_BEATMAP,Button.MARK_PLAYED,Button.CLEAR_SCORES,Button.EDIT_BEATMAP,Button.CLOSE);
        if(s.mode()==Mode.DELETE) return List.of(Button.CONFIRM,Button.CANCEL);
        if(s.edit()!=Edit.NONE) return List.of(Button.SAVE,Button.CANCEL);
        return List.of(Button.CREATE,Button.RENAME,Button.DELETE,Button.DIFFICULTY,Button.SET,Button.MISSING,Button.PREVIOUS,Button.NEXT,Button.CLOSE);
    }
    static boolean enabled(Snapshot s,Button b) {
        return switch(b) {
            case DELETE_BEATMAP,MARK_PLAYED,CLEAR_SCORES,EDIT_BEATMAP -> false;
            case RENAME,DELETE -> s.selected()!=null;
            case DIFFICULTY -> s.selected()!=null && s.hasDifficulty();
            case SET -> s.selected()!=null && s.hasSet();
            case MISSING -> s.selected()!=null && s.missing()>0;
            case PREVIOUS -> s.page()>0;
            case NEXT -> (s.page()+1)*PAGE_SIZE<s.rows().size();
            default -> true;
        };
    }
    static Bounds panel(UiLayout l) {
        float w=Math.min(860,l.width()-64), h=Math.min(530,l.height()-80); return new Bounds((l.width()-w)/2,(l.height()-h)/2,w,h);
    }
    static Bounds rowBounds(UiLayout l,int row) { var p=panel(l); return new Bounds(p.x()+20,p.y()+p.height()-140-row*38,p.width()*.42f,36); }
    static Bounds editor(UiLayout l) { var p=panel(l); return new Bounds(p.x()+24,p.y()+p.height()-172,p.width()-48,46); }
    static Bounds bounds(UiLayout l,Button button) {
        var p=panel(l); float right=p.x()+p.width()*.48f, width=p.width()*.52f-24;
        return switch(button) {
            case MANAGE -> new SongSelectSelectorLayout(l).option(0);
            case DELETE_BEATMAP -> new SongSelectSelectorLayout(l).option(1);
            case MARK_PLAYED -> new SongSelectSelectorLayout(l).option(2);
            case CLEAR_SCORES -> new SongSelectSelectorLayout(l).option(3);
            case EDIT_BEATMAP -> new SongSelectSelectorLayout(l).option(4);
            case CLOSE -> new Bounds(p.x()+p.width()-158,p.y()+16,134,32);
            case CREATE,RENAME,DELETE -> new Bounds(right,p.y()+p.height()-144-(button.ordinal()-Button.CREATE.ordinal())*40,width,34);
            case DIFFICULTY,SET,MISSING -> new Bounds(right,p.y()+p.height()-288-(button.ordinal()-Button.DIFFICULTY.ordinal())*40,width,34);
            case PREVIOUS -> new Bounds(p.x()+20,p.y()+28,92,30);
            case NEXT -> new Bounds(p.x()+124,p.y()+28,92,30);
            case SAVE,CONFIRM -> new Bounds(p.x()+24,p.y()+p.height()-232,p.width()/2-36,38);
            case CANCEL -> new Bounds(p.x()+p.width()/2+12,p.y()+p.height()-232,p.width()/2-36,38);
        };
    }
}
