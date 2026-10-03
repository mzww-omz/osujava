package dev.osujava.ui;

import java.util.Set;

/** Song Select owns selector state independently of browser ordering/search/selection.
 * These are audited UI capabilities, not gameplay mods. No unsupported capability can activate. */
final class SongSelectToolboxState {
    enum Overlay { NONE, MODE, MODS }
    enum Capability { IMPLEMENTED, PARTIAL, DEBUG_ONLY, ABSENT }
    enum Mod {
        EASY("EZ", "Easy", 0,0,"Q"), NO_FAIL("NF", "No Fail", 0,1,"W"), HALF_TIME("HT", "Half Time", 0,2,"E"),
        HARD_ROCK("HR", "Hard Rock", 1,0,"A"), SUDDEN_DEATH("SD", "Sudden Death", 1,1,"S"), DOUBLE_TIME("DT", "Double Time", 1,2,"D"),
        HIDDEN("HD", "Hidden", 1,3,"F"), FLASHLIGHT("FL", "Flashlight", 1,4,"G"),
        RELAX("RX", "Relax", 2,0,"Z"), AUTOPILOT("AP", "Autopilot", 2,1,"X"),
        SPUN_OUT("SO", "Spun Out", 2,2,"C"), AUTO("AT", "Auto", 2,3,"V"), SCORE_V2("V2", "Score V2", 2,4,"B");
        final String acronym, label;
        final int group,column;
        final String shortcut;
        Mod(String acronym, String label, int group,int column,String shortcut) { this.acronym = acronym; this.label = label; this.group = group;this.column=column;this.shortcut=shortcut; }
        dev.osujava.skin.SongSelectSkinAssets.Image image() {
            return switch (this) {
                case NO_FAIL -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_NF;
                case EASY -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_EZ;
                case HALF_TIME -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_HT;
                case HIDDEN -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_HD;
                case HARD_ROCK -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_HR;
                case SUDDEN_DEATH -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_SD;
                case DOUBLE_TIME -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_DT;
                case FLASHLIGHT -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_FL;
                case RELAX -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_RX;
                case AUTOPILOT -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_AP;
                case SPUN_OUT -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_SO;
                case AUTO -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_AUTO;
                case SCORE_V2 -> dev.osujava.skin.SongSelectSkinAssets.Image.MOD_SCORE_V2;
            };
        }
        Capability capability() { return this == AUTO ? Capability.DEBUG_ONLY : Capability.ABSENT; }
        boolean available() { return capability() == Capability.IMPLEMENTED; }
    }
    private Overlay overlay = Overlay.NONE;
    private Overlay drawing = Overlay.NONE;
    final SongSelectMenuAnimation animation=new SongSelectMenuAnimation();
    // No implemented ordinary Mods exist yet. A future selection must be validated against gameplay capabilities.
    private final Set<Mod> active = Set.of();
    Overlay overlay() { return overlay; }
    boolean open() { return overlay != Overlay.NONE; }
    void open(Overlay next) { if(next==Overlay.NONE) { close();return; } overlay=drawing=next;animation.open(); }
    void close() { overlay = Overlay.NONE;animation.close(); }
    Overlay drawing() { return drawing; }
    Set<Mod> active() { return active; }
    boolean toggle(Mod mod) { return false; }
    void reset() { /* There are no available gameplay Mods. */ }
}
