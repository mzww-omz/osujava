package dev.osujava.ruleset.osu;

import dev.osujava.beatmap.HitObject;
import dev.osujava.gameplay.Judgement;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Combo-set variant assignment at judgement time (stable 0600068d), not from final totals. */
final class OsuComboSets {
    enum Variant { NONE, NORMAL_END, KATU, GEKI }
    private final List<HitObject> objects;
    private final Map<HitObject, Integer> indices = new IdentityHashMap<>();
    private final boolean[] judged;
    private int failures, hundreds, geki, katu;

    OsuComboSets(List<HitObject> objects) {
        this.objects = objects.stream().filter(o -> o.type() != HitObject.Type.UNKNOWN).toList();
        judged = new boolean[this.objects.size()];
        for (int i = 0; i < this.objects.size(); i++) indices.put(this.objects.get(i), i);
    }

    Variant record(HitObject object, Judgement result) {
        int i = indices.get(object);
        if (judged[i]) throw new IllegalStateException("Object judged twice");
        judged[i] = true;
        if (result == Judgement.MISS || result == Judgement.HIT50) failures++;
        if (result == Judgement.HIT100) hundreds++;
        if (i + 1 < objects.size() && (objects.get(i + 1).rawType() & 4) == 0) return Variant.NONE;
        boolean pending = false;
        if (object.type() != HitObject.Type.SPINNER) {
            for (int j = i; j >= 0; j--) {
                pending |= !judged[j];
                if ((objects.get(j).rawType() & 4) != 0) break;
            }
        }
        Variant variant = Variant.NONE;
        if (result != Judgement.MISS) {
            if (!pending && failures == 0 && hundreds == 0) { geki++; variant = Variant.GEKI; }
            else if (!pending && failures == 0) { katu++; variant = Variant.KATU; }
            else variant = Variant.NORMAL_END;
        }
        failures = hundreds = 0;
        return variant;
    }

    int geki() { return geki; }
    int katu() { return katu; }
}
