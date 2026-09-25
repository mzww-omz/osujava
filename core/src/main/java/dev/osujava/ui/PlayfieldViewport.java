package dev.osujava.ui;

public record PlayfieldViewport(float left, float bottom, float scale) {
    public static PlayfieldViewport fit(float screenWidth, float screenHeight) {
        float scale = Math.min(screenWidth / 512f, screenHeight / 384f);
        return new PlayfieldViewport((screenWidth - 512 * scale) / 2,
                (screenHeight - 384 * scale) / 2, scale);
    }

    public float width() {
        return 512 * scale;
    }

    public float height() {
        return 384 * scale;
    }

    /** Converts a logical osu! playfield length to the fitted window length. */
    public float toScreenLength(double osuLength) {
        return (float) osuLength * scale;
    }

    /** Converts a window length back to the logical osu! playfield coordinate space. */
    public double toOsuLength(float screenLength) {
        return screenLength / scale;
    }

    public float toScreenX(double osuX) {
        return left + (float) osuX * scale;
    }

    public float toScreenY(double osuY) {
        return bottom + (384 - (float) osuY) * scale;
    }

    public boolean containsScreenPoint(float x, float y) {
        return x >= left && x <= left + width() && y >= bottom && y <= bottom + height();
    }

    public double toOsuX(float screenX) {
        return (screenX - left) / scale;
    }

    public double toOsuY(float screenY) {
        return 384 - (screenY - bottom) / scale;
    }
}
