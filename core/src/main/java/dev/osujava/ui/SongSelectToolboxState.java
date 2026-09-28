package dev.osujava.ui;

import java.util.Set;

/** Song Select owns selector state independently of browser ordering/search/selection.
 * These are audited UI capabilities, not gameplay mods. No unsupported capability can activate. */
final class SongSelectToolboxState {
    enum Overlay { NONE, MODE, MODS }
    enum Capability { IMPLEMENTED, PARTIAL, DEBUG_ONLY, ABSENT }
    enum Mod {
        NO_FAIL("NF", "No Fail", 0), EASY("EZ", "Easy", 0), HALF_TIME("HT", "Half Time", 0),
        HIDDEN("HD", "Hidden", 1), HARD_ROCK("HR", "Hard Rock", 1),
        SUDDEN_DEATH("SD", "Sudden Death", 1), DOUBLE_TIME("DT", "Double Time", 1),
        RELAX("RX", "Relax", 2), AUTOPILOT("AP", "Autopilot", 2),
        FLASHLIGHT("FL", "Flashlight", 1), SPUN_OUT("SO", "Spun Out", 2), AUTO("AT", "Auto", 2);
        final String acronym, label;
        final int group;
        Mod(String acronym, String label, int group) { this.acronym = acronym; this.label = label; this.group = group; }
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
            };
        }
        Capability capability() { return this == AUTO ? Capability.DEBUG_ONLY : Capability.ABSENT; }
        boolean available() { return capability() == Capability.IMPLEMENTED; }
    }
    private Overlay overlay = Overlay.NONE;
    // No implemented ordinary Mods exist yet. A future selection must be validated against gameplay capabilities.
    private final Set<Mod> active = Set.of();
    Overlay overlay() { return overlay; }
    boolean open() { return overlay != Overlay.NONE; }
    void open(Overlay next) { overlay = next; }
    void close() { overlay = Overlay.NONE; }
    Set<Mod> active() { return active; }
    boolean toggle(Mod mod) { return false; }
    void reset() { /* There are no available gameplay Mods. */ }
}
