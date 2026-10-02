package dev.osujava.score;

import java.util.UUID;

/** Explicit local identity; the name is a snapshot, not an online account. */
public record LocalPlayer(UUID id, String name) {
    public LocalPlayer {
        if (id == null || name == null) throw new IllegalArgumentException("Missing local player");
        name = name.strip();
        if (name.isEmpty() || name.codePointCount(0,name.length()) > 80 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid local player name");
    }
}
