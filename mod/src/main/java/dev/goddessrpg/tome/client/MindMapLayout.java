package dev.goddessrpg.tome.client;

import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.SkillDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure placement of the skill mind map (no Minecraft client classes, so it is unit-testable). The class is the centre,
 * its two fields sit left and right of it, and each skill sits at its data-file {@code map} position. Map units are
 * scaled to fit the given area, then overlapping nodes are pushed apart and everything is kept inside the area.
 */
public final class MindMapLayout {
    private MindMapLayout() {}

    /** Side of a node's square in pixels; node coordinates are centres. */
    public static final int NODE_SIZE = 16;
    /** Map-unit distance of the two field nodes from the class centre (first field right, second left). */
    public static final int FIELD_OFFSET = 50;
    private static final int GAP = 2;
    private static final double MAX_SCALE = 2.0;

    public enum State { LEARNED, KNOWN_LOCKED, UNDISCOVERED }

    public enum Kind { CLASS, FIELD, ACTIVE, PASSIVE }

    /** {@code id}: class id for CLASS, lower-case field name for FIELD, skill id otherwise. x/y: centre, area-relative. */
    public record Node(String id, int x, int y, State state, Kind kind) {}

    /** Skills shown on this player's map: own class, and branch skills only of the player's own subtype. */
    public static boolean visible(SkillDef s, RpgPlayerData d) {
        return d.rpgClass().filter(c -> c == s.rpgClass() && c.owns(s.field())).isPresent()
                && (s.subtype().isEmpty() || s.subtype().equals(d.subtype()));
    }

    /**
     * Learned skills are LEARNED. Otherwise proficiency skills are always KNOWN_LOCKED, book skills become
     * KNOWN_LOCKED once the player has held that book, and boss skills stay UNDISCOVERED.
     */
    public static State stateOf(SkillDef s, RpgPlayerData d) {
        if (d.learnedSkills().contains(s.id())) {
            return State.LEARNED;
        }
        return switch (s.unlock()) {
            case SkillDef.Unlock.Proficiency p -> State.KNOWN_LOCKED;
            case SkillDef.Unlock.Book b -> d.seenSkillBooks().contains(s.id()) ? State.KNOWN_LOCKED : State.UNDISCOVERED;
            case SkillDef.Unlock.Boss b -> State.UNDISCOVERED;
        };
    }

    public static String fieldId(Field f) {
        return f.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** An empty list when the player has no class yet (inquiry not done). */
    public static List<Node> layout(List<SkillDef> skills, RpgPlayerData d, int w, int h) {
        if (d.rpgClass().isEmpty()) {
            return List.of();
        }
        RpgClass c = d.rpgClass().get();
        List<SkillDef> shown = skills.stream().filter(s -> visible(s, d))
                .sorted(Comparator.comparing(SkillDef::id)).toList();

        List<String> ids = new ArrayList<>();
        List<State> states = new ArrayList<>();
        List<Kind> kinds = new ArrayList<>();
        List<int[]> map = new ArrayList<>();
        ids.add(c.id());
        states.add(State.LEARNED);
        kinds.add(Kind.CLASS);
        map.add(new int[] {0, 0});
        ids.add(fieldId(c.first()));
        states.add(State.LEARNED);
        kinds.add(Kind.FIELD);
        map.add(new int[] {FIELD_OFFSET, 0});
        ids.add(fieldId(c.second()));
        states.add(State.LEARNED);
        kinds.add(Kind.FIELD);
        map.add(new int[] {-FIELD_OFFSET, 0});
        for (SkillDef s : shown) {
            ids.add(s.id());
            states.add(stateOf(s, d));
            kinds.add(s.kind() == SkillDef.Kind.ACTIVE ? Kind.ACTIVE : Kind.PASSIVE);
            map.add(new int[] {s.map().x(), s.map().y()});
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] p : map) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        int half = NODE_SIZE / 2;
        double innerW = Math.max(1, w - NODE_SIZE);
        double innerH = Math.max(1, h - NODE_SIZE);
        double scale = Math.min(MAX_SCALE, Math.min(innerW / Math.max(1, maxX - minX), innerH / Math.max(1, maxY - minY)));
        double midX = (minX + maxX) / 2.0;
        double midY = (minY + maxY) / 2.0;

        int n = map.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = w / 2.0 + (map.get(i)[0] - midX) * scale;
            ys[i] = h / 2.0 + (map.get(i)[1] - midY) * scale;
        }
        relax(xs, ys, w, h, half);

        List<Node> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Node(ids.get(i), clampInt(xs[i], half, w - half), clampInt(ys[i], half, h - half),
                    states.get(i), kinds.get(i)));
        }
        return out;
    }

    /** Pushes overlapping nodes apart along their wider axis; deterministic, a bounded number of rounds. */
    private static void relax(double[] xs, double[] ys, int w, int h, int half) {
        double need = NODE_SIZE + GAP;
        for (int round = 0; round < 200; round++) {
            boolean moved = false;
            for (int i = 0; i < xs.length; i++) {
                for (int j = i + 1; j < xs.length; j++) {
                    double dx = xs[j] - xs[i];
                    double dy = ys[j] - ys[i];
                    if (Math.abs(dx) >= need || Math.abs(dy) >= need) {
                        continue;
                    }
                    moved = true;
                    if (Math.abs(dx) >= Math.abs(dy)) {
                        double push = (need - Math.abs(dx)) / 2 + 0.5;
                        double sign = dx == 0 ? 1 : Math.signum(dx);
                        xs[i] -= sign * push;
                        xs[j] += sign * push;
                    } else {
                        double push = (need - Math.abs(dy)) / 2 + 0.5;
                        double sign = Math.signum(dy);
                        ys[i] -= sign * push;
                        ys[j] += sign * push;
                    }
                }
            }
            for (int i = 0; i < xs.length; i++) {
                xs[i] = clamp(xs[i], half, w - half);
                ys[i] = clamp(ys[i], half, h - half);
            }
            if (!moved) {
                return;
            }
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return hi < lo ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(double v, int lo, int hi) {
        return (int) Math.round(clamp(Math.round(v), lo, hi));
    }

    /** The node whose square contains the area-relative point. */
    public static Optional<Node> hit(List<Node> nodes, int x, int y) {
        int half = NODE_SIZE / 2;
        for (Node n : nodes) {
            if (x >= n.x() - half && x < n.x() + half && y >= n.y() - half && y < n.y() + half) {
                return Optional.of(n);
            }
        }
        return Optional.empty();
    }
}
