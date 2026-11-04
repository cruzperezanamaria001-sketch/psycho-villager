package com.psycho.villager.command;

import com.mojang.brigadier.CommandDispatcher;
import com.psycho.villager.data.PsychoData;
import com.psycho.villager.logic.PsychoManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * /psycho spawn  -> aparece un aldeano asesino donde estas
 * /psycho kill   -> mata al aldeano asesino
 * /psycho attack -> el asesino ataca ahora mismo al aldeano/jugador mas cercano
 */
public final class PsychoCommand {
    private PsychoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("psycho")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn").executes(ctx -> spawn(ctx.getSource())))
                .then(Commands.literal("kill").executes(ctx -> kill(ctx.getSource())))
                .then(Commands.literal("attack").executes(ctx -> attack(ctx.getSource()))));
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
}
