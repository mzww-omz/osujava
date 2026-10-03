package dev.osujava.ui;

import com.badlogic.gdx.graphics.Color;
import dev.osujava.ui.theme.*;
import dev.osujava.ui.SongSelectCollections.*;
import static dev.osujava.ui.SongSelectCollections.PAGE_SIZE;
import static dev.osujava.ui.SongSelectCollections.enabled;

/** Draws the cached local collection dialog over the existing Song Select foreground. */
final class SongSelectCollectionsOverlay {
    private static final Color DIM=new Color(0,0,0,.70f), PANEL=new Color(.035f,.03f,.055f,.98f), TILE=new Color(.16f,.14f,.20f,1);
    static void draw(UiView view,UiLayout layout,Snapshot s,SongSelectMenuAnimation animation,float px,float py) {
        if(!s.open()) return;
        if(s.mode()==Mode.OPTIONS) { options(view,layout,s,animation,px,py);return; }
        var p=SongSelectCollections.panel(layout);
        view.beginShapes(); view.box(0,0,layout.width(),layout.height(),0,DIM); box(view,p,PANEL);
        if(s.mode()==Mode.MANAGE && s.edit()==Edit.NONE) for(int i=0;i<PAGE_SIZE;i++) {
            int index=s.page()*PAGE_SIZE+i; if(index>=s.rows().size()) break;
            var row=SongSelectCollections.rowBounds(layout,i);
            box(view,row,s.rows().get(index).id().equals(s.selected()) ? UiTheme.ACCENT : row.contains(px,py) ? UiTheme.SURFACE_RAISED : TILE);
        }
        if(s.edit()!=Edit.NONE) box(view,SongSelectCollections.editor(layout),TILE);
        for(var button:SongSelectCollections.buttons(s)) {
            var b=SongSelectCollections.bounds(layout,button); box(view,b,enabled(s,button) && b.contains(px,py) ? UiTheme.ACCENT : TILE);
        }
        view.endShapes(); view.beginText();
        view.textSmoothBold(s.mode()==Mode.OPTIONS ? "Beatmap Options" : s.mode()==Mode.DELETE ? "Delete collection?" : "Manage Collections",
                p.x()+24,p.y()+p.height()-36,p.width()-48,1.2f,UiTheme.TEXT);
        view.textSmooth(s.target(),p.x()+24,p.y()+p.height()-68,p.width()-48,.78f,UiTheme.MUTED);
        if(s.mode()==Mode.MANAGE && s.edit()==Edit.NONE) {
            if(s.rows().isEmpty()) view.textSmooth("No collections. Create one to start.",p.x()+24,p.y()+p.height()-116,p.width()*.42f,.75f,UiTheme.MUTED);
            for(int i=0;i<PAGE_SIZE;i++) {
                int index=s.page()*PAGE_SIZE+i; if(index>=s.rows().size()) break;
                var b=SongSelectCollections.rowBounds(layout,i); var row=s.rows().get(index);
                view.textSmooth(row.name(),b.x()+8,b.y()+22,b.width()-16,.74f,UiTheme.TEXT);
                view.textSmooth(row.count(),b.x()+8,b.y()+7,b.width()-16,.53f,UiTheme.MUTED);
            }
            view.textSmooth("Page "+(s.page()+1)+" / "+Math.max(1,(s.rows().size()+PAGE_SIZE-1)/PAGE_SIZE),p.x()+24,p.y()+72,p.width()*.42f,.62f,UiTheme.MUTED);
        } else if(s.edit()!=Edit.NONE) {
            view.textSmooth(s.edit()==Edit.CREATE ? "New collection name" : "Collection name",p.x()+24,p.y()+p.height()-106,p.width()-48,.85f,UiTheme.TEXT);
            var b=SongSelectCollections.editor(layout);
            view.textSmooth(s.draft()+"|",b.x()+10,b.y()+16,b.width()-20,.90f,UiTheme.TEXT);
        } else if(s.mode()==Mode.DELETE) {
            view.textSmooth(s.selectedName(),p.x()+24,p.y()+p.height()-114,p.width()-48,1,UiTheme.TEXT);
            view.textSmooth("Beatmaps and local scores will stay in your library.",p.x()+24,p.y()+p.height()-150,p.width()-48,.80f,UiTheme.MUTED);
        }
        for(var button:SongSelectCollections.buttons(s)) {
            var b=SongSelectCollections.bounds(layout,button);
            view.textSmooth(label(s,button),b.x()+10,b.y()+b.height()*.50f-4,b.width()-20,.74f,enabled(s,button) ? UiTheme.TEXT : UiTheme.MUTED);
        }
        if(!s.error().isEmpty()) view.textSmooth(s.error(),p.x()+p.width()*.48f,p.y()+76,p.width()*.52f-24,.72f,UiTheme.ERROR);
        view.endText();
    }
    private static String label(Snapshot s,Button b) {
        return switch(b) {
            case MANAGE -> "1  Manage Collections";
            case CLOSE -> "Esc  Close";
            case CREATE -> "New collection";
            case RENAME -> "Rename collection";
            case DELETE -> "Delete collection…";
            case DIFFICULTY -> s.difficultyIncluded() ? "Remove this difficulty" : "Add this difficulty";
            case SET -> s.setIncluded() ? "Remove this mapset" : "Add this mapset";
            case MISSING -> "Remove missing maps ("+s.missing()+")";
            case PREVIOUS -> "‹ Previous";
            case NEXT -> "Next ›";
            case SAVE -> "Save";
            case CANCEL -> "Cancel";
            case CONFIRM -> "Delete collection";
            case DELETE_BEATMAP -> "2. Delete…";
            case MARK_PLAYED -> "3. Remove from Unplayed";
            case CLEAR_SCORES -> "4. Clear local scores";
            case EDIT_BEATMAP -> "5. Edit";
        };
    }
    private static final Color OPTIONS_GREEN=new Color(.6f,.8f,.1f,1), OPTIONS_RED=new Color(1,.27f,0,1), OPTIONS_PURPLE=new Color(.82f,.49f,.85f,1), OPTIONS_GREY=new Color(.5f,.5f,.5f,1), OPTIONS_TINT=new Color();
    private static void options(UiView view,UiLayout layout,Snapshot s,SongSelectMenuAnimation animation,float px,float py) {
        float scale=new SongSelectSelectorLayout(layout).scale,alpha=animation.opacity();
        view.beginShapes();view.box(0,0,layout.width(),layout.height(),0,OPTIONS_TINT.set(0,0,0,SongSelectToolboxOverlay.DIALOG_DIM_ALPHA*alpha));
        int index=0;
        for(var button:SongSelectCollections.buttons(s)) SongSelectToolboxOverlay.dialogButton(view,SongSelectCollections.optionBounds(layout,button),optionLabel(button),optionColor(button),animation,index++,px,py,enabled(s,button),false);
        view.endShapes();view.beginText();
        view.textSmooth(s.target(),5*scale,layout.height()-24*scale,layout.width()-10*scale,20f/17*scale,OPTIONS_TINT.set(1,1,1,alpha));
        view.textSmooth("What would you like to do with this beatmap?",5*scale,layout.height()-48*scale,layout.width()-10*scale,20f/17*scale,OPTIONS_TINT);
        index=0;
        for(var button:SongSelectCollections.buttons(s)) {
            var b=SongSelectCollections.optionBounds(layout,button);
            SongSelectToolboxOverlay.dialogButton(view,b,optionLabel(button),optionColor(button),animation,index,px,py,enabled(s,button),true);
            if(!enabled(s,button) && animation.bounds(b,index).contains(px,py)) view.textSmooth("This action is not available in osu!java yet.",5*scale,layout.height()-444*scale,layout.width()-10*scale,.65f*scale,OPTIONS_TINT.set(1,1,1,alpha));
            index++;
        }
        view.endText();
    }
    private static String optionLabel(Button b) { return switch(b) {case MANAGE->"1. Manage Collections";case DELETE_BEATMAP->"2. Delete…";case MARK_PLAYED->"3. Remove from Unplayed";case CLEAR_SCORES->"4. Clear local scores";case EDIT_BEATMAP->"5. Edit";case CLOSE->"6. Cancel";default->throw new IllegalArgumentException("Not an Options command");}; }
    private static Color optionColor(Button b) { return switch(b) {case MANAGE->OPTIONS_GREEN;case DELETE_BEATMAP,EDIT_BEATMAP->OPTIONS_RED;case MARK_PLAYED,CLEAR_SCORES->OPTIONS_PURPLE;default->OPTIONS_GREY;}; }
    private static void box(UiView view,SongSelectToolboxLayout.Bounds b,Color color) { view.box(b.x(),b.y(),b.width(),b.height(),0,color); }
}
