package com.psycho.villager.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.psycho.villager.data.PsychoData;
import com.psycho.villager.logic.Interrogation;
import com.psycho.villager.logic.InvestigationLog;
import com.psycho.villager.logic.PsychoManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * /psycho spawn          -> aparece un aldeano asesino donde estas        (operador)
 * /psycho kill           -> mata al aldeano asesino                       (operador)
 * /psycho attack         -> ataca ahora al aldeano/jugador mas cercano    (operador)
 * /psycho window attack  -> rompe una ventana, entra agachado y ataca     (operador)
 * /psycho book           -> te da el libro Registro de investigaciones    (todos)
 * /psycho ask <uuid> <n> -> lo usa el menu de interrogatorio (clic)       (todos)
 */
public final class PsychoCommand {
    private PsychoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("psycho")
                .then(Commands.literal("spawn")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> spawn(ctx.getSource())))
                .then(Commands.literal("kill")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> kill(ctx.getSource())))
                .then(Commands.literal("attack")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> attack(ctx.getSource())))
                .then(Commands.literal("window")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("attack").executes(ctx -> windowAttack(ctx.getSource()))))
                .then(Commands.literal("book")
                        .executes(ctx -> book(ctx.getSource())))
                .then(Commands.literal("ask")
                        .then(Commands.argument("villager", UuidArgument.uuid())
                                .then(Commands.argument("question", IntegerArgumentType.integer(1, 4))
                                        .executes(ctx -> ask(ctx.getSource(),
                                                UuidArgument.getUuid(ctx, "villager"),
                                                IntegerArgumentType.getInteger(ctx, "question")))))));
    }

    private static int book(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack book = InvestigationLog.create(source.getLevel());
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }
        source.sendSuccess(() -> Component.literal("Registro de investigaciones entregado. Ábrelo con clic derecho."), false);
        return 1;
    }

    private static int ask(CommandSourceStack source, UUID id, int question) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Entity entity = source.getLevel().getEntity(id);
        if (!(entity instanceof Villager villager) || !villager.isAlive()) {
            source.sendFailure(Component.literal("Ese aldeano ya no está."));
            return 0;
        }
        Interrogation.answer(player, villager, question);
        return 1;
    }

    private static int spawn(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            source.sendFailure(Component.literal("No se pudo crear al aldeano asesino."));
            return 0;
        }
        Vec3 pos = source.getPosition();
        villager.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()),
                MobSpawnType.COMMAND, null, null);
        PsychoManager.makeKiller(villager);
        level.addFreshEntity(villager);
        PsychoData.get(level).setKillerChosen(true);
        source.sendSuccess(() -> Component.literal("Ha aparecido un aldeano asesino."), true);
        return 1;
    }

    private static int kill(CommandSourceStack source) {
        List<PsychoManager.KillerState> killers = PsychoManager.allKillers();
        int count = 0;
        for (PsychoManager.KillerState state : killers) {
            Villager villager = state.villager;
            if (villager.isRemoved() || !villager.isAlive()) {
                continue;
            }
            villager.hurt(villager.level().damageSources().genericKill(), Float.MAX_VALUE);
            count++;
        }
        if (count == 0) {
            source.sendFailure(Component.literal("No hay ningun aldeano asesino cargado en el mundo."));
            return 0;
        }
        final int total = count;
        source.sendSuccess(() -> Component.literal("Aldeano asesino eliminado (" + total + ")."), true);
        return total;
    }

    private static int attack(CommandSourceStack source) {
        List<PsychoManager.KillerState> killers = PsychoManager.allKillers();
        int count = 0;
        boolean anyKiller = false;
        for (PsychoManager.KillerState state : killers) {
            if (state.villager.isRemoved() || !state.villager.isAlive()) {
                continue;
            }
            anyKiller = true;
            if (PsychoManager.forceAttack(state)) {
                count++;
            }
        }
        if (!anyKiller) {
            source.sendFailure(Component.literal("No hay ningun aldeano asesino cargado en el mundo."));
            return 0;
        }
        if (count == 0) {
            source.sendFailure(Component.literal("El asesino no encuentra a nadie cerca para atacar."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("¡El aldeano asesino ataca ahora mismo!"), true);
        return count;
    }

    private static int windowAttack(CommandSourceStack source) {
        List<PsychoManager.KillerState> killers = PsychoManager.allKillers();
        int count = 0;
        boolean anyKiller = false;
        for (PsychoManager.KillerState state : killers) {
            if (state.villager.isRemoved() || !state.villager.isAlive()) {
                continue;
            }
            anyKiller = true;
            if (PsychoManager.forceWindowAttack(state)) {
                count++;
            }
        }
        if (!anyKiller) {
            source.sendFailure(Component.literal("No hay ningun aldeano asesino cargado en el mundo."));
            return 0;
        }
        if (count == 0) {
            source.sendFailure(Component.literal(
                    "No encuentra ninguna ventana (de cristal) cerca de una victima por la que entrar."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("El aldeano asesino va a una ventana para entrar a matar..."), true);
        return count;
    }
}
