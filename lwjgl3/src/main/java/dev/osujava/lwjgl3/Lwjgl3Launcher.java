package dev.osujava.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.osujava.OsuJavaGame;

public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("osu!java");
        configuration.setWindowedMode(1100, 720);
        configuration.useVsync(true);
        configuration.setForegroundFPS(120);
        new Lwjgl3Application(new OsuJavaGame(), configuration);
    }
}

