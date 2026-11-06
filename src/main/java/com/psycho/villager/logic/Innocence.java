package com.psycho.villager.logic;

import com.psycho.villager.Config;
import com.psycho.villager.block.BloodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fingir inocencia: mientras no esta cazando, el asesino es un aldeano normal que reacciona si lo miran.
 * Maquina de estados: INOCENTE (acciones falsas) <-> CAZANDO (PsychoManager). Al empezar a cazar se
 * cancela cualquier accion falsa, asi los comportamientos normales no estorban a los ataques.
 */
public final class Innocence {
    private Innocence() {}

    public enum Action { NONE, LOOK, TURN_AWAY, WALK_AWAY, ENTER_HOUSE, FAKE_TRADE, FAKE_WORK, FAKE_SLEEP, CHANGE_ZONE }

    // ------------------------------------------------------------------ observacion

    /** Un jugador que esta mirando directamente al asesino (y lo ve), o null. */
    public static ServerPlayer findWatcher(ServerLevel level, Villager killer, double range) {
        Vec3 eye = killer.getEyePosition();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            Vec3 to = eye.subtract(player.getEyePosition());
            double dist = to.length();
            if (dist > range || dist < 0.5D) {
                continue;
            }
            double dot = player.getViewVector(1.0F).dot(to.scale(1.0D / dist));
            if (dot > 0.96D && player.hasLineOfSight(killer)) {
                return player;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ sospecha

    public static void add(PsychoManager.KillerState st, int amount) {
        int old = st.suspicion;
        st.suspicion = Math.max(0, Math.min(100, st.suspicion + amount));
        if (amount > 0) {
            st.calmTicks = 0;
        }
        if (old != st.suspicion) {
            st.villager.getPersistentData().putInt("PsychoSuspicion", st.suspicion);
        }
    }

    /** Cada 5 ticks: mirarlo mucho o quedarse cerca sube la sospecha; si lo dejan en paz, baja. */
    public static void updateSuspicion(PsychoManager.KillerState st) {
        boolean watched = st.watcher != null;
        if (watched) {
            st.gazeTicks += 5;
            st.calmTicks = 0;
            if (st.gazeTicks >= Config.GAZE_THRESHOLD) {
                st.gazeTicks = Config.GAZE_THRESHOLD - 20;
                add(st, 2);
            }
        } else {
            st.gazeTicks = Math.max(0, st.gazeTicks - 5);
        }

        if (st.nearPlayer) {
            st.followTicks += 5;
            st.calmTicks = 0;
            if (st.followTicks >= Config.FOLLOW_THRESHOLD) {
                st.followTicks = 0;
                add(st, 3);
            }
        } else {
            st.followTicks = Math.max(0, st.followTicks - 5);
        }

        if (!watched && !st.nearPlayer) {
            st.calmTicks += 5;
            if (st.calmTicks >= 600 && (st.calmTicks - 600) % 100 == 0) {
                add(st, -1);
            }
        }
    }

    /** El jugador encontro pruebas relacionadas con el asesino. */
    public static void onEvidence(PsychoManager.KillerState st, int amount) {
        add(st, amount);
    }

    /** El jugador lo ataca: abandona la inocencia y se defiende. */
    public static void onAttacked(PsychoManager.KillerState st) {
        add(st, 100);
        endFake(st);
    }

    // ------------------------------------------------------------------ estado inocente

    /** Se llama cada tick mientras el asesino NO esta cazando. */
    public static void tickInnocent(ServerLevel level, PsychoManager.KillerState st, boolean night) {
        Villager k = st.villager;
        if (st.reactCooldown > 0) {
            st.reactCooldown--;
        }
        if (st.zoneCooldown > 0) {
            st.zoneCooldown--;
        }
        if (st.cleanCooldown > 0) {
            st.cleanCooldown--;
        }

        // Con mucha sospecha esconde las pruebas que tenga cerca.
        if (st.suspicion >= Config.SUSPICION_HIDE && st.cleanCooldown <= 0 && k.tickCount % 20 == 0) {
            hideEvidence(level, st);
        }

        if (st.fakeAction != Action.NONE) {
            runAction(level, st);
            return;
        }
        if (k.tickCount % 5 == 0) {
            chooseAction(level, st, night);
        }
    }

    private static void chooseAction(ServerLevel level, PsychoManager.KillerState st, boolean night) {
        Villager k = st.villager;
        RandomSource random = level.random;
        Player near = level.getNearestPlayer(k, 24.0D);

        // Muy sospechoso: cambia de zona.
        if (st.suspicion >= Config.SUSPICION_ZONE && st.zoneCooldown <= 0 && near != null) {
            start(st, Action.CHANGE_ZONE, 600);
            st.fakeDest = randomZone(level, k, random);
            st.zoneCooldown = 2400;
            return;
        }
        if (st.reactCooldown > 0) {
            return;
        }
        // Sospechoso: evita quedarse cerca del jugador.
        if (st.suspicion >= Config.SUSPICION_AVOID && st.nearPlayer && near != null) {
            begin(level, st, Action.WALK_AWAY, near);
            st.reactCooldown = 200;
            return;
        }
        // Lo estan mirando: reaccion al azar.
        ServerPlayer watcher = st.watcher;
        if (watcher != null) {
            int roll = random.nextInt(100);
            Action action = roll < 30 ? Action.LOOK
                    : roll < 50 ? Action.TURN_AWAY
                    : roll < 65 ? Action.WALK_AWAY
                    : roll < 80 ? Action.ENTER_HOUSE
                    : roll < 90 ? Action.FAKE_TRADE
                    : Action.FAKE_WORK;
            if (!begin(level, st, action, watcher)) {
                begin(level, st, Action.LOOK, watcher);
            }
            st.reactCooldown = 300;
            return;
        }
        // De noche, con un jugador cerca, finge dormir.
        if (night && near != null && near.distanceToSqr(k) < 144.0D && random.nextInt(100) < 2) {
            if (begin(level, st, Action.FAKE_SLEEP, near)) {
                st.reactCooldown = 400;
            }
            return;
        }
        // De vez en cuando se acerca a otros aldeanos como si nada.
        if (random.nextInt(1000) < 2 && begin(level, st, Action.FAKE_TRADE, near)) {
            st.reactCooldown = 300;
        }
    }

    private static void start(PsychoManager.KillerState st, Action action, int ticks) {
        st.fakeAction = action;
        st.fakeTicks = ticks;
        st.fakeDest = null;
        st.fakeLookId = null;
    }

    /** Empieza una accion falsa. Devuelve false si no se puede (por ejemplo, no tiene casa). */
    private static boolean begin(ServerLevel level, PsychoManager.KillerState st, Action action, Player player) {
        Villager k = st.villager;
        RandomSource random = level.random;
        switch (action) {
            case LOOK -> {
                if (player == null) {
                    return false;
                }
                start(st, action, 40 + random.nextInt(40));
                st.fakeLookId = player.getUUID();
                return true;
            }
            case TURN_AWAY -> {
                if (player == null) {
                    return false;
                }
                start(st, action, 30);
                st.fakeLookId = player.getUUID();
                return true;
            }
            case WALK_AWAY -> {
                if (player == null) {
                    return false;
                }
                start(st, action, 200);
                st.fakeDest = awayFrom(k, player.position(), 10.0D);
                return true;
            }
            case ENTER_HOUSE -> {
                BlockPos home = home(k);
                if (home == null) {
                    return false;
                }
                start(st, action, 500);
                st.fakeDest = Vec3.atCenterOf(home);
                return true;
            }
            case FAKE_TRADE -> {
                Villager other = nearestOther(level, k, 12.0D);
                if (other == null) {
                    return false;
                }
                start(st, action, 300);
                st.fakeLookId = other.getUUID();
                return true;
            }
            case FAKE_WORK -> {
                BlockPos job = job(k);
                if (job == null) {
                    return false;
                }
                start(st, action, 300);
                st.fakeDest = Vec3.atCenterOf(job);
                return true;
            }
            case FAKE_SLEEP -> {
                BlockPos home = home(k);
                if (home == null || !level.getBlockState(home).is(BlockTags.BEDS)) {
                    return false;
                }
                start(st, action, 2400);
                st.fakeDest = Vec3.atCenterOf(home);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void runAction(ServerLevel level, PsychoManager.KillerState st) {
        Villager k = st.villager;
        st.fakeTicks--;
        // Mientras finge, el cerebro normal no le manda a otros sitios.
        k.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        k.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (st.fakeTicks <= 0) {
            endFake(st);
            return;
        }

        switch (st.fakeAction) {
            case LOOK -> {
                k.getNavigation().stop();
                Entity e = lookTarget(level, st);
                if (e != null) {
                    k.getLookControl().setLookAt(e, 30.0F, 30.0F);
                }
            }
            case TURN_AWAY -> {
                k.getNavigation().stop();
                Entity e = lookTarget(level, st);
                if (e != null) {
                    k.getLookControl().setLookAt(k.getX() * 2.0D - e.getX(), k.getEyeY(), k.getZ() * 2.0D - e.getZ());
                }
            }
            case WALK_AWAY, CHANGE_ZONE -> {
                double speed = st.fakeAction == Action.WALK_AWAY ? 0.45D : 0.5D;
                if (walkTo(k, st.fakeDest, speed, st.fakeAction == Action.WALK_AWAY ? 2.0D : 4.0D)) {
                    endFake(st);
                }
            }
            case ENTER_HOUSE -> {
                // Cuando llega a casa, se queda un rato dentro.
                if (walkTo(k, st.fakeDest, 0.5D, 2.0D) && st.fakeTicks > 120) {
                    st.fakeTicks = 120;
                }
            }
            case FAKE_TRADE -> {
                Entity other = lookTarget(level, st);
                if (other == null || other.isRemoved()) {
                    endFake(st);
                    return;
                }
                if (k.distanceToSqr(other) > 6.25D) {
                    walkTo(k, other.position(), 0.5D, 2.0D);
                } else {
                    k.getNavigation().stop();
                    k.getLookControl().setLookAt(other, 30.0F, 30.0F);
                    if (other instanceof Villager v) {
                        v.getLookControl().setLookAt(k, 30.0F, 30.0F);
                    }
                    if (k.tickCount % 40 == 0) {
                        level.playSound(null, k.blockPosition(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.8F, 1.0F);
                    }
                }
            }
            case FAKE_WORK -> {
                if (walkTo(k, st.fakeDest, 0.5D, 1.5D)) {
                    k.getNavigation().stop();
                    if (k.tickCount % 30 == 0) {
                        k.swing(InteractionHand.MAIN_HAND);
                    }
                }
            }
            case FAKE_SLEEP -> {
                if (walkTo(k, st.fakeDest, 0.5D, 2.5D) && !k.isSleeping()) {
                    BlockPos bed = BlockPos.containing(st.fakeDest);
                    if (level.getBlockState(bed).is(BlockTags.BEDS)) {
                        k.startSleeping(bed);
                    } else {
                        endFake(st);
                    }
                }
            }
            default -> endFake(st);
        }
    }

    /** Cancela la accion falsa (al terminar, o cuando empieza a cazar o lo atacan). */
    public static void endFake(PsychoManager.KillerState st) {
        if (st.fakeAction == Action.NONE) {
            return;
        }
        Villager k = st.villager;
        st.fakeAction = Action.NONE;
        st.fakeDest = null;
        st.fakeLookId = null;
        st.fakeTicks = 0;
        k.getNavigation().stop();
        if (k.isSleeping()) {
            k.stopSleeping();
        }
    }

    // ------------------------------------------------------------------ esconder pruebas

    /** Recoge la lista de nombres que haya soltado y limpia la sangre de alrededor. */
    private static void hideEvidence(ServerLevel level, PsychoManager.KillerState st) {
        Villager k = st.villager;
        st.cleanCooldown = 100;

        AABB area = k.getBoundingBox().inflate(8.0D, 3.0D, 8.0D);
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, area, i -> {
            ItemStack stack = i.getItem();
            return stack.is(Items.WRITTEN_BOOK) && stack.hasTag() && stack.getTag().getBoolean("PsychoList");
        })) {
            k.getInventory().addItem(drop.getItem().copy());
            drop.discard();
            level.sendParticles(ParticleTypes.POOF, drop.getX(), drop.getY() + 0.3D, drop.getZ(), 6, 0.2D, 0.2D, 0.2D, 0.01D);
        }

        int cleaned = 0;
        BlockPos base = k.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6, -1, -6), base.offset(6, 2, 6))) {
            if (cleaned >= 6) {
                break;
            }
            if (level.getBlockState(pos).getBlock() instanceof BloodBlock) {
                level.removeBlock(pos, false);
                cleaned++;
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static Entity lookTarget(ServerLevel level, PsychoManager.KillerState st) {
        return st.fakeLookId == null ? null : level.getEntity(st.fakeLookId);
    }

    /** Camina hacia el destino. Devuelve true cuando ya llego. */
    private static boolean walkTo(Villager k, Vec3 dest, double speed, double arriveDistance) {
        if (dest == null) {
            return true;
        }
        if (k.distanceToSqr(dest) <= arriveDistance * arriveDistance) {
            k.getNavigation().stop();
            return true;
        }
        if (k.tickCount % 20 == 0 || k.getNavigation().isDone()) {
            k.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
        }
        return false;
    }

    private static Vec3 awayFrom(Villager k, Vec3 from, double distance) {
        Vec3 dir = new Vec3(k.getX() - from.x, 0.0D, k.getZ() - from.z);
        if (dir.lengthSqr() < 0.01D) {
            dir = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return k.position().add(dir.normalize().scale(distance));
    }

    private static Vec3 randomZone(ServerLevel level, Villager k, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double dist = 30.0D + random.nextInt(16);
        int x = Mth.floor(k.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(k.getZ() + Math.sin(angle) * dist);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= level.getMinBuildHeight()) {
            y = (int) k.getY();
        }
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    private static BlockPos home(Villager k) {
        return k.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
    }

    private static BlockPos job(Villager k) {
        return k.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
    }

    private static Villager nearestOther(ServerLevel level, Villager k, double range) {
        Villager best = null;
        double bestDist = Double.MAX_VALUE;
        for (Villager v : level.getEntitiesOfClass(Villager.class, k.getBoundingBox().inflate(range),
                v -> v != k && v.isAlive() && !PsychoManager.isKiller(v))) {
            double d = k.distanceToSqr(v);
            if (d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return best;
    }
}
