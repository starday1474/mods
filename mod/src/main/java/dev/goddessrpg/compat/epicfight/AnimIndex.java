package dev.goddessrpg.compat.epicfight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.goddessrpg.GoddessRpg;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * {@code data/goddessrpg/anim_index.json}, written by tools/anim/build_anims.py: every generated animation with its
 * kind (attack / loop / action), length and attack phase. It is read straight from the mod jar through the mod's own
 * class loader because animations are registered during mod construction, before any resource manager exists.
 */
public final class AnimIndex {
    private AnimIndex() {}

    public static final String RESOURCE = "data/" + GoddessRpg.MODID + "/anim_index.json";
    private static final Logger LOG = LogUtils.getLogger();

    public enum Kind { ATTACK, LOOP, ACTION }

    public record Phase(float antic, float contact, float recovery) {}

    public record Entry(String name, Kind kind, float length, Phase phase) {
        public String path() {
            return "biped/combat/" + name;
        }

        public String key() {
            return GoddessRpg.MODID + ":" + path();
        }
    }

    private static List<Entry> cache;

    public static synchronized List<Entry> entries() {
        if (cache == null) {
            cache = load();
        }
        return cache;
    }

    private static List<Entry> load() {
        try (InputStream in = AnimIndex.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOG.error("{} missing from the mod jar; no goddessrpg animations beyond balanced_auto1-3 are registered", RESOURCE);
                return List.of();
            }
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not read {}", RESOURCE, e);
            return List.of();
        }
    }

    static List<Entry> parse(JsonElement root) {
        List<Entry> out = new ArrayList<>();
        JsonArray arr = root.getAsJsonArray();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            Phase phase = null;
            if (o.has("phase") && o.get("phase").isJsonObject()) {
                JsonObject p = o.getAsJsonObject("phase");
                phase = new Phase(p.get("antic").getAsFloat(), p.get("contact").getAsFloat(), p.get("recovery").getAsFloat());
            }
            out.add(new Entry(o.get("name").getAsString(), Kind.valueOf(o.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)),
                    o.get("length").getAsFloat(), phase));
        }
        return List.copyOf(out);
    }

    public static Entry find(String name) {
        for (Entry e : entries()) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        return null;
    }
}
