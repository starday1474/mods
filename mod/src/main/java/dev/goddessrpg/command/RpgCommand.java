package dev.goddessrpg.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.goddessrpg.skill.SkillRegistry;
import dev.goddessrpg.skill.SkillService;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.goddessrpg.data.RpgDataLoader;
import dev.goddessrpg.player.RpgAttachments;
import dev.goddessrpg.player.RpgPlayerData;
import dev.goddessrpg.rules.Field;
import dev.goddessrpg.rules.LevelProgress;
import dev.goddessrpg.rules.ProficiencyRules;
import dev.goddessrpg.rules.RpgClass;
import dev.goddessrpg.compat.epicfight.AnimIndex;
import dev.goddessrpg.compat.epicfight.AnimPlayer;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RpgCommand {
    private static final int MAX_STARS = 5;

    private RpgCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("goddessrpg")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("info")
                        .executes(c -> info(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> info(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("xp")
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Field.values()).map(f -> f.name().toLowerCase()), b))
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                                        .executes(RpgCommand::xp))))
                .then(Commands.literal("skill")
                        .then(Commands.literal("list")
                                .executes(c -> skillList(c, c.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(c -> skillList(c, EntityArgument.getPlayer(c, "player")))))
                        .then(Commands.literal("learn")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SkillRegistry.all().keySet(), b))
                                        .executes(c -> skillLearn(c, c.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> skillLearn(c, EntityArgument.getPlayer(c, "player"))))))
                        .then(Commands.literal("slot")
                                .then(Commands.argument("slot", IntegerArgumentType.integer(1, RpgPlayerData.SLOT_COUNT))
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        java.util.stream.Stream.concat(java.util.stream.Stream.of("none"),
                                                                SkillRegistry.all().keySet().stream()), b))
                                                .executes(RpgCommand::skillSlot)))))
                .then(Commands.literal("level")
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Field.values()).map(f -> f.name().toLowerCase()), b))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                        .executes(c -> level(c, c.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(c -> level(c, EntityArgument.getPlayer(c, "player")))))))
                .then(Commands.literal("anim")
                        .then(Commands.literal("list").executes(RpgCommand::animList))
                        .then(Commands.literal("play")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                AnimIndex.entries().stream().map(AnimIndex.Entry::name), b))
                                        .executes(RpgCommand::animPlay)))
                        .then(Commands.literal("stop").executes(RpgCommand::animStop)))
                .then(Commands.literal("reset")
                        .executes(c -> reset(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> reset(c, EntityArgument.getPlayer(c, "player"))))));
    }

    public static String stars(int n) {
        int s = Math.max(0, Math.min(MAX_STARS, n));
        return "★".repeat(s) + "☆".repeat(MAX_STARS - s);
    }

    private static int info(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        RpgPlayerData data = RpgAttachments.get(target);
        CommandSourceStack src = c.getSource();
        src.sendSuccess(() -> Component.translatable("goddessrpg.info.header", target.getDisplayName()), false);
        if (!data.inquiryDone() || data.rpgClass().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("goddessrpg.info.no_inquiry"), false);
            return 1;
        }
        RpgClass cls = data.rpgClass().get();
        String sub = data.subtype().orElse("?");
        src.sendSuccess(() -> Component.translatable("goddessrpg.info.class",
                Component.translatable("goddessrpg.class." + cls.id()),
                Component.translatable("goddessrpg.subtype." + sub)), false);
        for (Field f : Field.values()) {
            src.sendSuccess(() -> Component.translatable("goddessrpg.info.field",
                    Component.translatable("goddessrpg.field." + f.name().toLowerCase()),
                    data.levelOf(f),
                    String.format("%.1f", data.xpOf(f)),
                    stars(data.talentOf(f)),
                    stars(data.aptitudeOf(f))), false);
        }
        src.sendSuccess(() -> Component.translatable("goddessrpg.info.knowledge",
                String.format("%.2f", data.knowledge()), data.tomesRead().size()), false);
        return 1;
    }

    private static int xp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "field");
        Field f = Field.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive(name.toLowerCase())).result().orElse(null);
        if (f == null) {
            throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.command.unknown_field", name)).create();
        }
        RpgPlayerData data = RpgAttachments.get(p);
        if (!data.inquiryDone()) {
            throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.command.no_inquiry")).create();
        }
        double amount = DoubleArgumentType.getDouble(c, "amount");
        LevelProgress lp = addXp(p, f, amount);
        c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.xp",
                Component.translatable("goddessrpg.field." + f.name().toLowerCase()), lp.level()), true);
        return 1;
    }

    /** Adds raw XP (no multiplier), saves, and runs auto-learn plus the level-up message if the level rose. */
    public static LevelProgress addXp(ServerPlayer p, Field f, double amount) {
        RpgPlayerData data = RpgAttachments.get(p);
        int before = data.levelOf(f);
        LevelProgress lp = ProficiencyRules.addXp(RpgDataLoader.tuning(), before, data.xpOf(f), amount);
        RpgAttachments.set(p, data.withXp(f, lp));
        dev.goddessrpg.growth.GrowthEvents.onLevelChange(p, f, before, lp.level());
        return lp;
    }

    private static int animList(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        var entries = AnimIndex.entries();
        src.sendSuccess(() -> Component.translatable("goddessrpg.anim.list.header", entries.size()), false);
        for (AnimIndex.Entry e : entries) {
            Component name = Component.literal(e.name()).withStyle(st -> st
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/goddessrpg anim play " + e.name())));
            src.sendSuccess(() -> Component.translatable("goddessrpg.anim.list.entry", name,
                    Component.translatable("goddessrpg.anim.kind." + e.kind().name().toLowerCase(Locale.ROOT)),
                    String.format(Locale.ROOT, "%.2f", e.length())), false);
        }
        return entries.size();
    }

    private static int animPlay(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "name");
        ServerPlayer p = c.getSource().getPlayerOrException();
        switch (AnimPlayer.play(p, name)) {
            case UNKNOWN -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.anim.unknown", name)).create();
            case NOT_REGISTERED -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.anim.not_registered", name)).create();
            case NO_PATCH -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.anim.no_patch")).create();
            case OK -> {
                c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.anim.played", name), false);
                if (AnimPlayer.attackSpeedMismatch(p, name)) {
                    c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.anim.attack_speed_hint"), false);
                }
            }
        }
        return 1;
    }

    private static int animStop(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        switch (AnimPlayer.stop(p)) {
            case NO_PATCH -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.anim.no_patch_stop")).create();
            case NOTHING_PLAYING -> c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.anim.stop.nothing"), false);
            case STOPPED -> c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.anim.stopped"), false);
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        dev.goddessrpg.creation.CreationService.reopen(target, true);
        c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.reset", target.getDisplayName()), true);
        return 1;
    }

    private static Field parseField(String name) throws CommandSyntaxException {
        Field f = Field.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                new com.google.gson.JsonPrimitive(name.toLowerCase(Locale.ROOT))).result().orElse(null);
        if (f == null) {
            throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.command.unknown_field", name)).create();
        }
        return f;
    }

    /** Sets a proficiency level directly (clamped to 0..max, xp 0) and runs auto-learn. */
    public static int setLevel(ServerPlayer target, Field f, int level) {
        int lv = Math.max(0, Math.min(RpgDataLoader.tuning().maxLevel(), level));
        RpgAttachments.set(target, RpgAttachments.get(target).withXp(f, new LevelProgress(lv, 0.0)));
        SkillService.grantMetProficiencySkills(target);
        return lv;
    }

    private static int level(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException {
        Field f = parseField(StringArgumentType.getString(c, "field"));
        if (!RpgAttachments.get(target).inquiryDone()) {
            throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.command.no_inquiry")).create();
        }
        int lv = setLevel(target, f, IntegerArgumentType.getInteger(c, "level"));
        c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.level",
                target.getDisplayName(), Component.translatable("goddessrpg.field." + f.name().toLowerCase(Locale.ROOT)), lv), true);
        return 1;
    }

    private static int skillList(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        RpgPlayerData d = RpgAttachments.get(target);
        CommandSourceStack src = c.getSource();
        src.sendSuccess(() -> Component.translatable("goddessrpg.command.skill.list.header", target.getDisplayName(),
                d.learnedSkills().size()), false);
        d.learnedSkills().stream().sorted().forEach(id -> src.sendSuccess(
                () -> Component.literal(" - " + id + " ").append(SkillService.skillName(id)), false));
        for (int i = 0; i < d.slots().size(); i++) {
            int n = i + 1;
            var s = d.slots().get(i);
            src.sendSuccess(() -> Component.translatable("goddessrpg.command.skill.list.slot", n,
                    s.<Component>map(SkillService::skillName).orElse(Component.translatable("goddessrpg.skill.empty"))), false);
        }
        return d.learnedSkills().size();
    }

    private static int skillLearn(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "id");
        switch (SkillService.learn(target, id)) {
            case OK -> c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.skill.learned",
                    target.getDisplayName(), SkillService.skillName(id)), true);
            case ALREADY -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.skillbook.already")).create();
            case NOT_ALLOWED -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.skillbook.not_your_path")).create();
            case UNKNOWN -> throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.command.skill.unknown", id)).create();
        }
        return 1;
    }

    private static int skillSlot(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int slot = IntegerArgumentType.getInteger(c, "slot");
        String id = StringArgumentType.getString(c, "id");
        java.util.Optional<String> v = id.equals("none") ? java.util.Optional.empty() : java.util.Optional.of(id);
        if (!SkillService.setSlot(p, slot - 1, v)) {
            throw new SimpleCommandExceptionType(Component.translatable("goddessrpg.command.skill.slot_refused", id)).create();
        }
        c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.skill.slot", slot, id), false);
        return 1;
    }
}
