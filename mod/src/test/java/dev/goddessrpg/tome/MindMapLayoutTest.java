package dev.goddessrpg.tome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.SkillDef;
import dev.goddessrpg.rules.StarResult;
import dev.goddessrpg.tome.client.MindMapLayout;
import dev.goddessrpg.tome.client.MindMapLayout.Kind;
import dev.goddessrpg.tome.client.MindMapLayout.Node;
import dev.goddessrpg.tome.client.MindMapLayout.State;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MindMapLayoutTest {
    private static final List<String> REAL = List.of("balanced_shield_bash", "balanced_dash_slash", "balanced_counter",
            "balanced_steadfast", "vanguard_sword_mastery", "vanguard_shield_mastery");

    private static SkillDef load(String id) throws Exception {
        InputStream in = MindMapLayoutTest.class.getResourceAsStream("/data/goddessrpg/skills/" + id + ".json");
        assertNotNull(in, "missing skill " + id);
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            var j = JsonParser.parseReader(r).getAsJsonObject();
            j.addProperty("id", id);
            return SkillDef.CODEC.parse(JsonOps.INSTANCE, j).getOrThrow();
        }
    }

    private static List<SkillDef> real() throws Exception {
        List<SkillDef> out = new ArrayList<>();
        for (String id : REAL) {
            out.add(load(id));
        }
        return out;
    }

    private static SkillDef synthetic(String id, RpgClass c, Optional<String> subtype, Field f, SkillDef.Unlock u, int x, int y) {
        return new SkillDef(id, c, subtype, f, SkillDef.Kind.ACTIVE, u, 1, 0, 0, Optional.empty(),
                new SkillDef.Effect("buff", Map.of()), new SkillDef.MapPos(x, y));
    }

    private static RpgPlayerData balanced() {
        return RpgPlayerData.EMPTY.withResult(RpgClass.VANGUARD, "balanced", new StarResult(Map.of(), Map.of()),
                List.of(), GrowthTuning.DEFAULT);
    }

    private static Map<String, Node> byId(List<Node> nodes) {
        return nodes.stream().collect(Collectors.toMap(Node::id, n -> n));
    }

    @Test
    void emptyPlayer() throws Exception {
        List<SkillDef> skills = new ArrayList<>(real());
        skills.add(synthetic("x_boss", RpgClass.VANGUARD, Optional.empty(), Field.SWORD, new SkillDef.Unlock.Boss("dragon"), 0, -60));
        skills.add(synthetic("x_dealer", RpgClass.REAR_DEALER, Optional.empty(), Field.ARCHERY, new SkillDef.Unlock.Book(), 50, 50));
        skills.add(synthetic("x_tank", RpgClass.VANGUARD, Optional.of("tank"), Field.DEFENSE, new SkillDef.Unlock.Book(), -100, 60));
        RpgPlayerData d = balanced();
        assertTrue(d.learnedSkills().isEmpty());
        List<Node> nodes = MindMapLayout.layout(skills, d, 216, 112);
        Map<String, Node> m = byId(nodes);

        assertEquals(1, nodes.stream().filter(n -> n.kind() == Kind.CLASS).count());
        assertEquals(Kind.CLASS, m.get("vanguard").kind());
        assertEquals(2, nodes.stream().filter(n -> n.kind() == Kind.FIELD).count());
        assertEquals(Kind.FIELD, m.get("sword").kind());
        assertEquals(Kind.FIELD, m.get("defense").kind());
        // 3 structure nodes + 6 real skills + the boss skill; other class / other subtype skills are not on the map
        assertEquals(3 + REAL.size() + 1, nodes.size(), nodes.toString());
        assertFalse(m.containsKey("x_dealer"));
        assertFalse(m.containsKey("x_tank"));

        for (String id : List.of("balanced_shield_bash", "balanced_dash_slash", "balanced_counter",
                "vanguard_sword_mastery", "vanguard_shield_mastery")) {
            assertEquals(State.KNOWN_LOCKED, m.get(id).state(), id);
        }
        assertEquals(State.UNDISCOVERED, m.get("balanced_steadfast").state(), "unseen book skill");
        assertEquals(State.UNDISCOVERED, m.get("x_boss").state(), "boss skill");
        assertEquals(Kind.PASSIVE, m.get("vanguard_sword_mastery").kind());
        assertEquals(Kind.ACTIVE, m.get("balanced_dash_slash").kind());

        // seeing the book reveals it; boss skills stay hidden until learned
        Map<String, Node> seen = byId(MindMapLayout.layout(skills, d.withSeenSkillBooks(Set.of("balanced_steadfast")), 216, 112));
        assertEquals(State.KNOWN_LOCKED, seen.get("balanced_steadfast").state());
        assertEquals(State.UNDISCOVERED, seen.get("x_boss").state());
    }

    @Test
    void noClassMeansNoNodes() throws Exception {
        assertTrue(MindMapLayout.layout(real(), RpgPlayerData.EMPTY, 216, 112).isEmpty());
    }

    @Test
    void structureOnlyWithoutSkills() {
        List<Node> nodes = MindMapLayout.layout(List.of(), balanced(), 216, 112);
        assertEquals(3, nodes.size());
    }

    @Test
    void learnedShowsLearned() throws Exception {
        RpgPlayerData d = balanced().withLearned("balanced_dash_slash").withLearned("balanced_steadfast");
        Map<String, Node> m = byId(MindMapLayout.layout(real(), d, 216, 112));
        assertEquals(State.LEARNED, m.get("balanced_dash_slash").state());
        assertEquals(State.LEARNED, m.get("balanced_steadfast").state(), "a learned book skill is learned even if never seen");
        assertEquals(State.KNOWN_LOCKED, m.get("balanced_counter").state());
        assertEquals(State.LEARNED, m.get("vanguard").state(), "structure nodes are always bright");
    }

    @Test
    void noOverlap() throws Exception {
        int half = MindMapLayout.NODE_SIZE / 2;
        for (int[] s : new int[][] {{320, 240}, {1920, 1080}, {216, 112}}) {
            int w = s[0];
            int h = s[1];
            List<Node> nodes = MindMapLayout.layout(real(), balanced(), w, h);
            assertEquals(3 + REAL.size(), nodes.size());
            for (Node n : nodes) {
                assertTrue(n.x() - half >= 0 && n.y() - half >= 0 && n.x() + half <= w && n.y() + half <= h,
                        n + " outside " + w + "x" + h);
            }
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = i + 1; j < nodes.size(); j++) {
                    Node a = nodes.get(i);
                    Node b = nodes.get(j);
                    boolean overlap = Math.abs(a.x() - b.x()) < MindMapLayout.NODE_SIZE
                            && Math.abs(a.y() - b.y()) < MindMapLayout.NODE_SIZE;
                    assertFalse(overlap, a + " overlaps " + b + " at " + w + "x" + h);
                }
            }
            // hit testing finds every node at its own centre
            for (Node n : nodes) {
                assertEquals(Optional.of(n), MindMapLayout.hit(nodes, n.x(), n.y()));
            }
        }
    }
}
