package dev.goddessrpg.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.goddessrpg.GoddessRpg;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.InquiryData.Question;
import dev.goddessrpg.rules.InquiryData.Subtype;
import dev.goddessrpg.rules.RpgClass;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Loads tuning, subtypes and inquiry stages from data packs.
 * <ul>
 * <li>{@code data/goddessrpg/tuning/growth.json}</li>
 * <li>{@code data/goddessrpg/subtypes/<id>.json} ({@code {"class":..,"available":..}}, id = file name)</li>
 * <li>{@code data/goddessrpg/inquiry/stage1.json}, {@code inquiry/stage2/<class>.json},
 *     {@code inquiry/stage3/<class>.json}: each an object {"questions":[...]}; missing files give empty lists</li>
 * <li>{@code data/goddessrpg/tomes/<id>.json} ({@code {"knowledge":..,"title":..}}, id = file name)</li>
 * </ul>
 */
public final class RpgDataLoader extends SimplePreparableReloadListener<RpgDataLoader.Snapshot> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Codec<List<Question>> QUESTIONS =
            Question.CODEC.listOf().fieldOf("questions").codec();

    public record Snapshot(GrowthTuning tuning, List<Subtype> subtypes, List<Question> stage1,
                           Map<RpgClass, List<Question>> stage2, Map<RpgClass, List<Question>> stage3,
                           Map<String, TomeData> tomes) {}

    private static volatile Snapshot current = new Snapshot(GrowthTuning.DEFAULT, List.of(), List.of(),
            new EnumMap<>(RpgClass.class), new EnumMap<>(RpgClass.class), Map.of());

    public static GrowthTuning tuning() {
        return current.tuning();
    }

    public static List<Subtype> subtypes() {
        return current.subtypes();
    }

    public static List<Question> stage1() {
        return current.stage1();
    }

    public static List<Question> stage2(RpgClass c) {
        return current.stage2().getOrDefault(c, List.of());
    }

    public static List<Question> stage3(RpgClass c) {
        return current.stage3().getOrDefault(c, List.of());
    }

    public static Optional<TomeData> tome(String id) {
        return Optional.ofNullable(current.tomes().get(id));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GoddessRpg.MODID, path);
    }

    private static Optional<JsonElement> readJson(ResourceManager rm, ResourceLocation file) {
        var res = rm.getResource(file);
        if (res.isEmpty()) {
            return Optional.empty();
        }
        try (Reader r = res.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(r));
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to read {}", file, e);
            return Optional.empty();
        }
    }

    /** Parses an inquiry stage file of the form {"questions":[...]}; malformed input gives an empty list. */
    public static List<Question> parseQuestions(JsonElement json, String what) {
        return QUESTIONS.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> LOGGER.error("Bad inquiry file {}: {}", what, err))
                .orElse(List.of());
    }

    private static List<Question> readQuestions(ResourceManager rm, String path) {
        return readJson(rm, id(path)).map(json -> parseQuestions(json, path)).orElse(List.of());
    }

    @Override
    protected Snapshot prepare(ResourceManager rm, ProfilerFiller profiler) {
        GrowthTuning tuning = readJson(rm, id("tuning/growth.json"))
                .flatMap(j -> GrowthTuning.CODEC.parse(JsonOps.INSTANCE, j)
                        .resultOrPartial(err -> LOGGER.error("Bad growth.json: {}", err)))
                .orElse(GrowthTuning.DEFAULT);

        List<Subtype> subtypes = new ArrayList<>();
        rm.listResources("subtypes", l -> l.getNamespace().equals(GoddessRpg.MODID) && l.getPath().endsWith(".json"))
                .forEach((loc, res) -> {
                    String path = loc.getPath();
                    String name = path.substring("subtypes/".length(), path.length() - ".json".length());
                    readJson(rm, loc).ifPresent(j -> {
                        if (j.isJsonObject()) {
                            j.getAsJsonObject().addProperty("id", name);
                            Subtype.CODEC.parse(JsonOps.INSTANCE, j)
                                    .resultOrPartial(err -> LOGGER.error("Bad subtype {}: {}", name, err))
                                    .ifPresent(subtypes::add);
                        }
                    });
                });
        subtypes.sort(java.util.Comparator.comparing(Subtype::id));

        Map<RpgClass, List<Question>> s2 = new EnumMap<>(RpgClass.class);
        Map<RpgClass, List<Question>> s3 = new EnumMap<>(RpgClass.class);
        for (RpgClass c : RpgClass.values()) {
            s2.put(c, readQuestions(rm, "inquiry/stage2/" + c.id() + ".json"));
            s3.put(c, readQuestions(rm, "inquiry/stage3/" + c.id() + ".json"));
        }
        Map<String, TomeData> tomes = new java.util.HashMap<>();
        rm.listResources("tomes", l -> l.getNamespace().equals(GoddessRpg.MODID) && l.getPath().endsWith(".json"))
                .forEach((loc, res) -> {
                    String path = loc.getPath();
                    String name = path.substring("tomes/".length(), path.length() - ".json".length());
                    readJson(rm, loc).flatMap(j -> TomeData.CODEC.parse(JsonOps.INSTANCE, j)
                                    .resultOrPartial(err -> LOGGER.error("Bad tome {}: {}", name, err)))
                            .ifPresent(t -> tomes.put(name, t));
                });
        return new Snapshot(tuning, List.copyOf(subtypes), readQuestions(rm, "inquiry/stage1.json"), s2, s3,
                Map.copyOf(tomes));
    }

    @Override
    protected void apply(Snapshot snapshot, ResourceManager rm, ProfilerFiller profiler) {
        current = snapshot;
    }
}
