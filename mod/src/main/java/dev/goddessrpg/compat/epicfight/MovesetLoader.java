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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.gameasset.Animations;

/**
 * Weapon movesets from {@code data/goddessrpg/movesets/<name>.json}:
 * <pre>{"weapon_type":"goddessrpg:balanced_sword","base":"epicfight:sword","combo":[ID...],"dash":ID,"air":ID}</pre>
 * IDs are Epic Fight animation keys in the {@code epicfight:}, {@code epicfightx:} (Epic Fight Extra), {@code wom:}
 * (Weapons of Miracles) or {@code goddessrpg:} namespace.
 *
 * <p>The files are read when this class loads (from {@link RpgWeaponTypes}, during mod construction), straight from the
 * mod file like {@link AnimIndex}: weapon types and movesets are registered before any resource manager exists. The
 * animation IDs, however, are resolved only in {@link #resolve(Config)}, which the moveset supplier calls at registry
 * time: Epic Fight Extra and WOM fill {@code AnimationManager} from their own {@code AnimationRegistryEvent}, which runs
 * after every mod is constructed.
 *
 * <p>Epic Fight 21.17.3.1 API: {@code AnimationManager.byKey(String)} (null when not registered). Accessors are not
 * dereferenced here ({@code get()} would build the animation early); the attack type is checked by a game test.
 */
public final class MovesetLoader {
    private MovesetLoader() {}

    public static final String DIR = "data/" + GoddessRpg.MODID + "/movesets";
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> NAMESPACES = Set.of("epicfight", "epicfightx", "wom", GoddessRpg.MODID);
    /** Read when the mod file cannot be listed (e.g. outside FML). */
    private static final List<String> KNOWN = List.of("balanced");

    public record Config(String name, ResourceLocation weaponType, ResourceLocation base, List<String> combo, String dash,
            String air) {
        public Config {
            combo = List.copyOf(combo);
        }

        /** Combo attacks, then dash and air attack: Epic Fight's moveset order. */
        public List<String> attackIds() {
            List<String> out = new ArrayList<>(combo);
            out.add(dash);
            out.add(air);
            return List.copyOf(out);
        }
    }

    private static List<Config> cache;

    /** Every moveset file in the mod (sorted by name); unreadable files are logged and skipped. */
    public static synchronized List<Config> all() {
        if (cache == null) {
            List<Config> out = new ArrayList<>();
            for (String name : names()) {
                read(name).ifPresent(out::add);
            }
            cache = List.copyOf(out);
        }
        return cache;
    }

    private static List<String> names() {
        try {
            var info = ModList.get() == null ? null : ModList.get().getModFileById(GoddessRpg.MODID);
            if (info != null) {
                Path dir = info.getFile().findResource(DIR.split("/"));
                if (Files.isDirectory(dir)) {
                    try (Stream<Path> files = Files.list(dir)) {
                        return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                                .map(n -> n.substring(0, n.length() - ".json".length())).sorted().toList();
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not list {}; using {}", DIR, KNOWN, e);
        }
        return KNOWN;
    }

    /** {@code data/goddessrpg/movesets/<name>.json} through the mod's class loader; empty (and logged) when missing or bad. */
    public static Optional<Config> read(String name) {
        String res = DIR + "/" + name + ".json";
        try (InputStream in = MovesetLoader.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(parse(name, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))));
        } catch (IOException | RuntimeException e) {
            LOG.error("Bad moveset {}: {}", res, e.toString());
            return Optional.empty();
        }
    }

    /** Throws on a malformed file (missing field, empty combo). */
    static Config parse(String name, JsonElement root) {
        JsonObject o = root.getAsJsonObject();
        JsonArray arr = req(o, "combo").getAsJsonArray();
        List<String> combo = new ArrayList<>();
        for (JsonElement el : arr) {
            combo.add(el.getAsString());
        }
        if (combo.isEmpty()) {
            throw new IllegalArgumentException("empty combo");
        }
        return new Config(name, ResourceLocation.parse(req(o, "weapon_type").getAsString()),
                ResourceLocation.parse(req(o, "base").getAsString()), combo, req(o, "dash").getAsString(),
                req(o, "air").getAsString());
    }

    private static JsonElement req(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            throw new IllegalArgumentException("missing \"" + key + "\"");
        }
        return e;
    }

    /** A well-formed id in one of the four animation namespaces. */
    static boolean allowedId(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && id.indexOf(':') > 0 && NAMESPACES.contains(rl.getNamespace());
    }

    /** Epic Fight's own one-hand sword combo, dash and air slash. */
    public static List<AnimationAccessor<? extends AttackAnimation>> efSwordDefault() {
        return List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3, Animations.SWORD_DASH,
                Animations.SWORD_AIR_SLASH);
    }

    /**
     * The config's attacks as registered accessors. Any unknown id (bad namespace or not in {@code AnimationManager})
     * is logged and the whole moveset falls back to {@link #efSwordDefault()}, so a typo never leaves a half combo.
     */
    @SuppressWarnings("unchecked")
    public static List<AnimationAccessor<? extends AttackAnimation>> resolve(Config c) {
        List<AnimationAccessor<? extends AttackAnimation>> out = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String id : c.attackIds()) {
            AnimationAccessor<?> acc = null;
            if (allowedId(id)) {
                try {
                    acc = AnimationManager.byKey(id);
                } catch (RuntimeException e) {
                    acc = null;
                }
            }
            if (acc == null) {
                missing.add(id);
            } else {
                out.add((AnimationAccessor<? extends AttackAnimation>) acc);
            }
        }
        if (!missing.isEmpty()) {
            LOG.error("Moveset {} ({}): unknown animation id(s) {}; using Epic Fight's default sword combo", c.name(),
                    c.weaponType(), missing);
            return efSwordDefault();
        }
        return List.copyOf(out);
    }
}
