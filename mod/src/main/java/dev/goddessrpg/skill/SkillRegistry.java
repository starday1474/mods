package dev.goddessrpg.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.rules.SkillDef;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/** Loads {@code data/goddessrpg/skills/<id>.json}; the id is the file name. Bad files are logged and skipped. */
public final class SkillRegistry extends SimplePreparableReloadListener<Map<String, SkillDef>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<String, SkillDef> current = Map.of();

    public static Map<String, SkillDef> all() {
        return current;
    }

    public static Optional<SkillDef> get(String id) {
        return Optional.ofNullable(current.get(id));
    }

    @Override
    protected Map<String, SkillDef> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<String, SkillDef> out = new LinkedHashMap<>();
        rm.listResources("skills", l -> l.getNamespace().equals(GoddessRpg.MODID) && l.getPath().endsWith(".json"))
                .forEach((loc, res) -> {
                    String path = loc.getPath();
                    String name = path.substring("skills/".length(), path.length() - ".json".length());
                    try (Reader r = res.openAsReader()) {
                        JsonElement j = JsonParser.parseReader(r);
                        if (!j.isJsonObject()) {
                            LOGGER.error("Skill {} is not a JSON object", name);
                            return;
                        }
                        j.getAsJsonObject().addProperty("id", name);
                        SkillDef.CODEC.parse(JsonOps.INSTANCE, j)
                                .resultOrPartial(err -> LOGGER.error("Bad skill {}: {}", name, err))
                                .ifPresent(s -> out.put(name, s));
                    } catch (Exception e) {
                        LOGGER.error("Failed to read skill {}", name, e);
                    }
                });
        return Map.copyOf(out);
    }

    @Override
    protected void apply(Map<String, SkillDef> snapshot, ResourceManager rm, ProfilerFiller profiler) {
        current = snapshot;
    }
}
