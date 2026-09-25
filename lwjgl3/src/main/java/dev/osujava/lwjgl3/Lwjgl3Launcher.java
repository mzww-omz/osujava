package dev.osujava.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.Gdx;
import dev.osujava.OsuJavaGame;

import java.util.Locale;

public final class Lwjgl3Launcher {
    private Lwjgl3Launcher() {
    }

    public static void main(String[] args) {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            Lwjgl3ApplicationConfiguration.useGlfwAsync();
        }
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("osu!java");
        configuration.setWindowedMode(1100, 720);
        configuration.useVsync(true);
        configuration.setForegroundFPS(120);
        configuration.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public boolean closeRequested() {
                Gdx.app.exit();
                return true;
            }
        });
        new Lwjgl3Application(new OsuJavaGame(new DesktopFileChooser()), configuration);
    }
}
