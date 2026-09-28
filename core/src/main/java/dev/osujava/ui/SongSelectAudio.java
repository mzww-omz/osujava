package dev.osujava.ui;

import com.badlogic.gdx.audio.Sound;
import dev.osujava.audio.AudioVolumes;
import dev.osujava.skin.SkinAssetResolver;
import java.util.*;
import java.util.function.Function;

/** Preloaded skin interface samples. Events are emitted by input/update, never drawing. */
final class SongSelectAudio implements AutoCloseable {
    enum Cue {
        EXPAND("select-expand"), DIFFICULTY("select-difficulty"), HOVER_ROW("menuclick"),
        BACK("menuback"), PLAY("menuhit"), CONFIRM("click-short-confirm"),
        HOVER_CONTROL("click-short"), HOVER_BACK("back-button-hover"),
        KEY1("key-press-1"), KEY2("key-press-2"), KEY3("key-press-3"), KEY4("key-press-4");
        final String filename;
        Cue(String filename) { this.filename = filename; }
    }
    private final Map<Cue, Sound> sounds = new EnumMap<>(Cue.class);
    private final AudioVolumes volumes;
    private String hover;
    private int key;
    SongSelectAudio(SkinAssetResolver resolver, Function<SkinAssetResolver.AssetFile, Sound> loader, AudioVolumes volumes) {
        this.volumes = volumes;
        for (Cue cue : Cue.values()) resolver.resolveNamedSound(cue.filename, file -> {
            try {
                Sound sound = loader.apply(file);
                if (sound == null) return false;
                sounds.put(cue, sound); return true;
            } catch (RuntimeException ignored) { return false; }
        });
    }
    void play(Cue cue) {
        Sound sound = sounds.get(cue);
        if (sound != null && volumes.effectOutput() > 0) {
            try { sound.play(volumes.effectOutput()); }
            catch (RuntimeException ignored) { /* A disconnected device must not interrupt input. */ }
        }
    }
    void typed() { play(Cue.values()[Cue.KEY1.ordinal() + key]); key = (key + 1) % 4; }
    void hover(String identity, Cue cue) {
        if (Objects.equals(hover, identity)) return;
        hover = identity;
        if (identity != null) play(cue);
    }
    void selection(SongBrowserModel.Selection previous, SongBrowserModel.Selection next) {
        if (next == null || Objects.equals(previous, next)) return;
        play(previous == null || !previous.setId().equals(next.setId()) ? Cue.EXPAND : Cue.DIFFICULTY);
    }
    @Override public void close() {
        Set<Sound> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        owned.addAll(sounds.values()); sounds.clear(); hover = null;
        for (Sound sound : owned) {
            try { sound.dispose(); } catch (RuntimeException ignored) { }
        }
    }
}
