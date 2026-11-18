package dev.goddessrpg.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.GrowthTuning;
import dev.goddessrpg.rules.LevelProgress;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.rules.StarResult;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable per-player RPG state. Every mutator returns a new instance. */
public record RpgPlayerData(
        boolean inquiryDone,
        Optional<RpgClass> rpgClass,
        Optional<String> subtype,
        Map<Field, Integer> talent,
        Map<Field, Integer> aptitude,
        Map<Field, Integer> level,
        Map<Field, Double> xp,
        Set<String> tomesRead,
        double knowledge,
        List<Integer> inquiryAnswers,
        Set<String> learnedSkills,
        List<Optional<String>> slots,
        Set<String> seenSkillBooks) {

    public static final int SLOT_COUNT = 4;
    private static final List<Optional<String>> NO_SLOTS = List.of(Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty());

    public static final RpgPlayerData EMPTY = new RpgPlayerData(
            false, Optional.empty(), Optional.empty(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(), 0.0, List.of(), Set.of(), NO_SLOTS, Set.of());

    private static final Codec<Map<Field, Integer>> FIELD_INTS = Codec.unboundedMap(Field.CODEC, Codec.INT);
    private static final Codec<Map<Field, Double>> FIELD_DOUBLES = Codec.unboundedMap(Field.CODEC, Codec.DOUBLE);

    public static final Codec<RpgPlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("inquiry_done", false).forGetter(RpgPlayerData::inquiryDone),
            RpgClass.CODEC.optionalFieldOf("rpg_class").forGetter(RpgPlayerData::rpgClass),
            Codec.STRING.optionalFieldOf("subtype").forGetter(RpgPlayerData::subtype),
            FIELD_INTS.optionalFieldOf("talent", Map.of()).forGetter(RpgPlayerData::talent),
            FIELD_INTS.optionalFieldOf("aptitude", Map.of()).forGetter(RpgPlayerData::aptitude),
            FIELD_INTS.optionalFieldOf("level", Map.of()).forGetter(RpgPlayerData::level),
            FIELD_DOUBLES.optionalFieldOf("xp", Map.of()).forGetter(RpgPlayerData::xp),
            Codec.STRING.listOf().optionalFieldOf("tomes_read", List.of()).xmap(Set::copyOf, List::copyOf)
                    .forGetter(RpgPlayerData::tomesRead),
            Codec.DOUBLE.optionalFieldOf("knowledge", 0.0).forGetter(RpgPlayerData::knowledge),
            Codec.INT.listOf().optionalFieldOf("inquiry_answers", List.of()).forGetter(RpgPlayerData::inquiryAnswers),
            Codec.STRING.listOf().optionalFieldOf("learned_skills", List.of()).xmap(Set::copyOf, l -> l.stream().sorted().toList())
                    .forGetter(RpgPlayerData::learnedSkills),
            // an empty slot is stored as ""
            Codec.STRING.listOf().optionalFieldOf("slots", List.of())
                    .xmap(l -> l.stream().map(x -> x.isEmpty() ? Optional.<String>empty() : Optional.of(x)).toList(),
                            l -> l.stream().map(o -> o.orElse("")).toList())
                    .forGetter(RpgPlayerData::slots),
            // skill book ids the player has held at least once (the tome's mind map reveals those book skills)
            Codec.STRING.listOf().optionalFieldOf("seen_skill_books", List.of()).xmap(Set::copyOf, l -> l.stream().sorted().toList())
                    .forGetter(RpgPlayerData::seenSkillBooks)
    ).apply(i, RpgPlayerData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RpgPlayerData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public RpgPlayerData {
        talent = Map.copyOf(talent);
        aptitude = Map.copyOf(aptitude);
        level = Map.copyOf(level);
        xp = Map.copyOf(xp);
        tomesRead = Set.copyOf(tomesRead);
        inquiryAnswers = List.copyOf(inquiryAnswers);
        learnedSkills = Set.copyOf(learnedSkills);
        List<Optional<String>> fixed = new java.util.ArrayList<>(slots);
        while (fixed.size() < SLOT_COUNT) {
            fixed.add(Optional.empty());
        }
        slots = List.copyOf(fixed.subList(0, SLOT_COUNT));
        seenSkillBooks = Set.copyOf(seenSkillBooks);
    }

    /** Without seen skill books (kept for callers written before that field existed). */
    public RpgPlayerData(boolean inquiryDone, Optional<RpgClass> rpgClass, Optional<String> subtype,
                         Map<Field, Integer> talent, Map<Field, Integer> aptitude, Map<Field, Integer> level,
                         Map<Field, Double> xp, Set<String> tomesRead, double knowledge, List<Integer> inquiryAnswers,
                         Set<String> learnedSkills, List<Optional<String>> slots) {
        this(inquiryDone, rpgClass, subtype, talent, aptitude, level, xp, tomesRead, knowledge, inquiryAnswers,
                learnedSkills, slots, Set.of());
    }

    public int levelOf(Field f) {
        return level.getOrDefault(f, 0);
    }

    public double xpOf(Field f) {
        return xp.getOrDefault(f, 0.0);
    }

    public int talentOf(Field f) {
        return talent.getOrDefault(f, 3);
    }

    public int aptitudeOf(Field f) {
        return aptitude.getOrDefault(f, 3);
    }

    public RpgPlayerData withResult(RpgClass c, String subtype, StarResult stars, List<Integer> answers, GrowthTuning t) {
        Map<Field, Integer> lv = new EnumMap<>(Field.class);
        Map<Field, Double> xpMap = new EnumMap<>(Field.class);
        for (Field f : Field.values()) {
            int start = 0;
            if (c.owns(f)) {
                start = t.talentStartLevel().getOrDefault(stars.talent().getOrDefault(f, 3), 0);
            }
            lv.put(f, start);
            xpMap.put(f, 0.0);
        }
        return new RpgPlayerData(true, Optional.of(c), Optional.of(subtype), stars.talent(), stars.aptitude(),
                lv, xpMap, tomesRead, knowledge, answers, learnedSkills, slots, seenSkillBooks);
    }

    public RpgPlayerData withXp(Field f, LevelProgress p) {
        Map<Field, Integer> lv = new EnumMap<>(Field.class);
        lv.putAll(level);
        lv.put(f, p.level());
        Map<Field, Double> xpMap = new EnumMap<>(Field.class);
        xpMap.putAll(xp);
        xpMap.put(f, p.xp());
        return new RpgPlayerData(inquiryDone, rpgClass, subtype, talent, aptitude, lv, xpMap, tomesRead, knowledge,
                inquiryAnswers, learnedSkills, slots, seenSkillBooks);
    }

    public RpgPlayerData withTome(String id, double gain, double cap) {
        if (tomesRead.contains(id)) {
            return this;
        }
        Set<String> tomes = new HashSet<>(tomesRead);
        tomes.add(id);
        double k = Math.min(cap, knowledge + gain);
        return new RpgPlayerData(inquiryDone, rpgClass, subtype, talent, aptitude, level, xp, tomes, k, inquiryAnswers, learnedSkills, slots, seenSkillBooks);
    }

    /** Admin reset: back to a fresh, un-inquired state; tomes, knowledge and seen skill books are kept. */
    public RpgPlayerData resetKeepKnowledge() {
        return new RpgPlayerData(false, Optional.empty(), Optional.empty(), Map.of(), Map.of(), Map.of(), Map.of(),
                tomesRead, knowledge, List.of(), Set.of(), NO_SLOTS, seenSkillBooks);
    }

    public RpgPlayerData withLearned(String id) {
        if (learnedSkills.contains(id)) {
            return this;
        }
        Set<String> l = new HashSet<>(learnedSkills);
        l.add(id);
        return new RpgPlayerData(inquiryDone, rpgClass, subtype, talent, aptitude, level, xp, tomesRead, knowledge,
                inquiryAnswers, l, slots, seenSkillBooks);
    }

    /**
     * Puts a learned active skill into a slot (or clears it with empty). The same skill is never in two slots.
     *
     * @throws IllegalArgumentException for a bad index, an unlearned skill, or a passive/unknown skill
     */
    public RpgPlayerData withSlot(int index, Optional<String> id) {
        if (index < 0 || index >= SLOT_COUNT) {
            throw new IllegalArgumentException("slot index " + index);
        }
        List<Optional<String>> s = new java.util.ArrayList<>(slots);
        if (id.isPresent()) {
            String sid = id.get();
            if (!learnedSkills.contains(sid)) {
                throw new IllegalArgumentException("not learned: " + sid);
            }
            var def = dev.goddessrpg.skill.SkillRegistry.get(sid);
            if (def.isEmpty() || def.get().kind() != dev.goddessrpg.rules.SkillDef.Kind.ACTIVE) {
                throw new IllegalArgumentException("not an active skill: " + sid);
            }
            for (int i = 0; i < s.size(); i++) {
                if (id.equals(s.get(i))) {
                    s.set(i, Optional.empty());
                }
            }
        }
        s.set(index, id);
        return new RpgPlayerData(inquiryDone, rpgClass, subtype, talent, aptitude, level, xp, tomesRead, knowledge,
                inquiryAnswers, learnedSkills, s, seenSkillBooks);
    }

    /** Adds skill book ids to the seen set; returns this instance when nothing is new. */
    public RpgPlayerData withSeenSkillBooks(java.util.Collection<String> ids) {
        if (seenSkillBooks.containsAll(ids)) {
            return this;
        }
        Set<String> seen = new HashSet<>(seenSkillBooks);
        seen.addAll(ids);
        return new RpgPlayerData(inquiryDone, rpgClass, subtype, talent, aptitude, level, xp, tomesRead, knowledge,
                inquiryAnswers, learnedSkills, slots, seen);
    }
}
