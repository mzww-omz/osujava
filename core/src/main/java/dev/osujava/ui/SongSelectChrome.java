package dev.osujava.ui;

/** Independently chosen viewport proportions; artwork may overlap these interaction regions. */
final class SongSelectChrome {
    private SongSelectChrome() { }
    static float bottomHeight(float height) { return height * (84f / 720); }
    static float cookieRadius(float height) { return height * .135f; }
    static float cookieX(float width, float radius) { return width - radius * .32f; }
    static float cookieY(float radius) { return radius * .42f; }
}
