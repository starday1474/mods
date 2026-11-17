package dev.goddessrpg.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
                .then(Commands.literal("anim")
                        .then(Commands.literal("list").executes(RpgCommand::animList))
                        .then(Commands.literal("play")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                AnimIndex.entries().stream().map(AnimIndex.Entry::name), b))
                                        .executes(RpgCommand::animPlay))))
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
        LevelProgress lp = ProficiencyRules.addXp(RpgDataLoader.tuning(), data.levelOf(f), data.xpOf(f), amount);
        RpgAttachments.set(p, data.withXp(f, lp));
        c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.xp",
                Component.translatable("goddessrpg.field." + f.name().toLowerCase()), lp.level()), true);
        return 1;
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
            case OK -> c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.anim.played", name), false);
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c, ServerPlayer target) {
        dev.goddessrpg.inquiry.InquiryService.reroll(target);
        c.getSource().sendSuccess(() -> Component.translatable("goddessrpg.command.reset", target.getDisplayName()), true);
        return 1;
    }
}
