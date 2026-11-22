package dev.goddessrpg.tome.client;

import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.tome.TomeRef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side copy of what the server sent for the adventurer's tome ({@code SkillDefsPayload}, {@code TomeRefPayload}).
 * Has no client-only imports, but is only ever filled on the client; cleared on logout.
 */
public final class ClientTomeData {
    private ClientTomeData() {}

    private static volatile Map<String, SkillDef> skills = Map.of();
    private static volatile TomeRef ref = TomeRef.EMPTY;

    public static void applySkills(List<SkillDef> defs) {
        Map<String, SkillDef> m = new LinkedHashMap<>();
        defs.forEach(d -> m.put(d.id(), d));
        skills = java.util.Collections.unmodifiableMap(m);
    }

    public static void applyRef(TomeRef r) {
        ref = r;
    }

    public static void reset() {
        skills = Map.of();
        ref = TomeRef.EMPTY;
    }

    public static List<SkillDef> skills() {
        return List.copyOf(skills.values());
    }

    public static Optional<SkillDef> skill(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public static TomeRef ref() {
        return ref;
    }
}
