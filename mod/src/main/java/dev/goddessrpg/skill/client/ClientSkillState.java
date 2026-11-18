package dev.goddessrpg.skill.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Client-only cooldown view. The server sends readyAt (server game time); the client keeps when it first saw each
 * cooldown so the overlay can show the fraction left without knowing the skill definitions.
 */
public final class ClientSkillState {
    private ClientSkillState() {}

    private record Entry(long readyAt, long seenAt) {}

    private static final Map<String, Entry> COOLDOWNS = new HashMap<>();

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void applyCooldowns(Map<String, Long> readyAt) {
        long now = now();
        Map<String, Entry> next = new HashMap<>();
        readyAt.forEach((id, at) -> {
            Entry old = COOLDOWNS.get(id);
            next.put(id, old != null && old.readyAt() == at ? old : new Entry(at, now));
        });
        COOLDOWNS.clear();
        COOLDOWNS.putAll(next);
    }

    public static void reset() {
        COOLDOWNS.clear();
    }

    /** Remaining ticks, 0 when ready. */
    public static long remainingTicks(String id) {
        Entry e = COOLDOWNS.get(id);
        return e == null ? 0 : Math.max(0, e.readyAt() - now());
    }

    /** Fraction of the cooldown still left, 0..1. */
    public static float remainingFraction(String id) {
        Entry e = COOLDOWNS.get(id);
        if (e == null) {
            return 0F;
        }
        long total = e.readyAt() - e.seenAt();
        long left = e.readyAt() - now();
        if (total <= 0 || left <= 0) {
            return 0F;
        }
        return Math.min(1F, (float) left / total);
    }
}
