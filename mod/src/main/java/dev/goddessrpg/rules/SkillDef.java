package dev.goddessrpg.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** A skill loaded from {@code data/goddessrpg/skills/<id>.json}; the id comes from the file name. */
public record SkillDef(
        String id,
        RpgClass rpgClass,
        Optional<String> subtype,
        Field field,
        Kind kind,
        Unlock unlock,
        double cooldownSec,
        double staminaCost,
        double manaCost,
        Optional<String> animation,
        Effect effect,
        MapPos map) {

    public enum Kind {
        ACTIVE, PASSIVE;

        public static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(s -> {
            try {
                return DataResult.success(valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown skill kind: " + s);
            }
        }, k -> k.name().toLowerCase(Locale.ROOT));
    }

    public sealed interface Unlock {
        record Proficiency(Field field, int level) implements Unlock {}

        record Book() implements Unlock {}

        record Boss(String boss) implements Unlock {}

        MapCodec<Proficiency> PROFICIENCY = RecordCodecBuilder.mapCodec(i -> i.group(
                Field.CODEC.fieldOf("field").forGetter(Proficiency::field),
                Codec.INT.fieldOf("level").forGetter(Proficiency::level)
        ).apply(i, Proficiency::new));
        MapCodec<Book> BOOK = MapCodec.unit(Book::new);
        MapCodec<Boss> BOSS = Codec.STRING.fieldOf("boss").xmap(Boss::new, Boss::boss);

        Codec<String> TYPE = Codec.STRING.validate(t -> switch (t) {
            case "proficiency", "book", "boss" -> DataResult.success(t);
            default -> DataResult.error(() -> "Unknown unlock type: " + t);
        });

        Codec<Unlock> CODEC = TYPE.dispatch("type", u -> switch (u) {
            case Proficiency p -> "proficiency";
            case Book b -> "book";
            case Boss b -> "boss";
        }, type -> switch (type) {
            case "proficiency" -> PROFICIENCY;
            case "book" -> BOOK;
            default -> BOSS; // "boss"; TYPE has already rejected anything else
        });
    }

    /**
     * Effect kind plus parameters; keys per kind are defined by the cast code ({@code SkillEffects}). Numbers go to
     * {@code params}; string values (e.g. {@code passive_attribute}'s {@code "requires": "sword"}) go to {@code text}.
     */
    public record Effect(String type, Map<String, Double> params, Map<String, String> text) {
        public Effect {
            params = Map.copyOf(params);
            text = Map.copyOf(text);
        }

        public Effect(String type, Map<String, Double> params) {
            this(type, params, Map.of());
        }

        public double param(String key, double def) {
            return params.getOrDefault(key, def);
        }

        public Optional<String> text(String key) {
            return Optional.ofNullable(text.get(key));
        }

        public static final Codec<Effect> CODEC = Codec.unboundedMap(Codec.STRING,
                        Codec.either(Codec.DOUBLE, Codec.STRING))
                .comapFlatMap(m -> {
                    var t = m.get("type");
                    if (t == null || t.left().isPresent()) {
                        return DataResult.error(() -> "Effect needs a string \"type\"");
                    }
                    Map<String, Double> params = new LinkedHashMap<>();
                    Map<String, String> text = new LinkedHashMap<>();
                    for (var e : m.entrySet()) {
                        if (e.getKey().equals("type")) {
                            continue;
                        }
                        e.getValue().ifLeft(v -> params.put(e.getKey(), v)).ifRight(v -> text.put(e.getKey(), v));
                    }
                    return DataResult.success(new Effect(t.right().get(), params, text));
                }, e -> {
                    Map<String, com.mojang.datafixers.util.Either<Double, String>> m = new LinkedHashMap<>();
                    m.put("type", com.mojang.datafixers.util.Either.right(e.type()));
                    e.params().forEach((k, v) -> m.put(k, com.mojang.datafixers.util.Either.left(v)));
                    e.text().forEach((k, v) -> m.put(k, com.mojang.datafixers.util.Either.right(v)));
                    return m;
                });
    }

    /** Mind-map position; the class center is (0,0). */
    public record MapPos(int x, int y) {
        public static final Codec<MapPos> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(MapPos::x),
                Codec.INT.fieldOf("y").forGetter(MapPos::y)
        ).apply(i, MapPos::new));
    }

    public static final Codec<SkillDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(SkillDef::id),
            RpgClass.CODEC.fieldOf("class").forGetter(SkillDef::rpgClass),
            Codec.STRING.optionalFieldOf("subtype").forGetter(SkillDef::subtype),
            Field.CODEC.fieldOf("field").forGetter(SkillDef::field),
            Kind.CODEC.fieldOf("kind").forGetter(SkillDef::kind),
            Unlock.CODEC.fieldOf("unlock").forGetter(SkillDef::unlock),
            Codec.DOUBLE.optionalFieldOf("cooldown", 0.0).forGetter(SkillDef::cooldownSec),
            Codec.DOUBLE.optionalFieldOf("stamina", 0.0).forGetter(SkillDef::staminaCost),
            Codec.DOUBLE.optionalFieldOf("mana", 0.0).forGetter(SkillDef::manaCost),
            Codec.STRING.optionalFieldOf("animation").forGetter(SkillDef::animation),
            Effect.CODEC.fieldOf("effect").forGetter(SkillDef::effect),
            MapPos.CODEC.fieldOf("map").forGetter(SkillDef::map)
    ).apply(i, SkillDef::new));
}
