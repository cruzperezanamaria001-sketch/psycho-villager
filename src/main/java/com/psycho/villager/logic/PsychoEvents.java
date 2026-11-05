package com.psycho.villager.logic;

import com.psycho.villager.PsychoVillagerMod;
import com.psycho.villager.command.PsychoCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del juego (bus de Forge). */
@Mod.EventBusSubscriber(modid = PsychoVillagerMod.MODID)
public final class PsychoEvents {
    private PsychoEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PsychoCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (entity instanceof Villager villager) {
            // Todos los aldeanos tienen nombre (tambien el asesino).
            VillagerNames.onJoin(villager, event.getLevel().getRandom());
            if (PsychoManager.isKiller(villager)) {
                PsychoManager.register(villager);
            }
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Villager) {
            PsychoManager.unregister(event.getEntity());
            VillagerNames.onLeave(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.level instanceof ServerLevel level) {
            PsychoManager.tickLevel(level);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();

        // Un jugador muere a manos del asesino: queda su cuerpo con su skin.
        if (dead instanceof ServerPlayer player) {
            PsychoManager.onPlayerDied(player);
            if (attacker instanceof Villager killer && PsychoManager.isKiller(killer)) {
                PsychoManager.onPlayerKilled(level, player, killer);
            }
            return;
        }

        if (!(dead instanceof Villager villager)) {
            return;
        }
        if (PsychoManager.isKiller(villager)) {
            PsychoManager.onKillerDeath(level, villager, attacker);
        } else if (attacker instanceof Villager killer && PsychoManager.isKiller(killer)) {
            PsychoManager.onMurder(level, villager, killer);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide() || !PsychoManager.isKiller(hurt)) {
            return;
        }
        if (event.getSource().getEntity() instanceof Player player) {
            PsychoManager.KillerState state = PsychoManager.getState(hurt);
            if (state != null) {
                PsychoManager.onKillerHurtByPlayer(state, player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PsychoManager.reset();
        VillagerNames.reset();
    }
}
