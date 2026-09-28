package dev.osujava.ui;

/** A position indicator, not a second source of scrolling state. */
final class SongSelectScrollbar {
    static SongSelectLayout.Rect thumb(float width, float bottom, float top, float offset, float range) {
        float height = top - bottom;
        if (range <= 0 || height <= 0) return new SongSelectLayout.Rect(width - 5,bottom,5,0);
        float size = Math.min(height,Math.max(18,height * height / (height + range)));
        float fraction = Math.max(0,Math.min(1,offset / range));
        return new SongSelectLayout.Rect(width - 5,top - size - fraction * (height-size),5,size);
    }
}
