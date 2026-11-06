package com.psycho.villager.logic;

import com.psycho.villager.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Testigos: los aldeanos que ven un asesinato lo recuerdan de verdad (guardado en su NBT),
 * y reaccionan: se esconden, buscan ayuda, se paralizan o huyen.
 */
public final class WitnessSystem {
    private WitnessSystem() {}

    /** Clave en el NBT persistente del aldeano donde guarda lo que vio. */
    public static final String KEY = "PsychoWitness";

    private enum Reaction { HIDE, HELP, FROZEN, ESCAPE }

    private static final class Behavior {
        final Villager villager;
        final Reaction type;
        final Vec3 threat;
        int ticks;

        Behavior(Villager villager, Reaction type, Vec3 threat, int ticks) {
            this.villager = villager;
            this.type = type;
            this.threat = threat;
            this.ticks = ticks;
        }
    }

    /** Despues del crimen, los testigos que siguen viendo al asesino anotan hacia donde se va. */
    private static final class PendingEscape {
        final ServerLevel level;
        final UUID killerId;
        final List<UUID> witnesses;
        final int crimeId;
        final Vec3 origin;
        int ticks = 60;

        PendingEscape(ServerLevel level, UUID killerId, List<UUID> witnesses, int crimeId, Vec3 origin) {
            this.level = level;
            this.killerId = killerId;
            this.witnesses = witnesses;
            this.crimeId = crimeId;
            this.origin = origin;
        }
    }

    private static final Map<UUID, Behavior> BEHAVIORS = new HashMap<>();
    private static final List<PendingEscape> PENDING = new ArrayList<>();

    public static void reset() {
        BEHAVIORS.clear();
        PENDING.clear();
    }

    // ------------------------------------------------------------------ memoria

    /** Lo que recuerda el aldeano, de lo mas reciente a lo mas antiguo. */
    public static List<CompoundTag> memories(Villager villager) {
        ListTag list = villager.getPersistentData().getList(KEY, Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            out.add(list.getCompound(i));
        }
        return out;
    }

    /** true si este aldeano vio y reconocio al asesino (el asesino lo prefiere como victima). */
    public static boolean knowsKiller(Villager witness, Villager killer) {
        if (!killer.hasCustomName()) {
            return false;
        }
        String killerName = killer.getCustomName().getString();
        ListTag list = witness.getPersistentData().getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            if (m.getBoolean("Identified") && killerName.equals(m.getString("Killer"))) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ crimen

    /** Se llama justo cuando el asesino mata, antes de quitar el cuerpo. */
    public static void onCrime(ServerLevel level, Villager killer, LivingEntity victim, String victimName, int crimeId) {
        AABB area = victim.getBoundingBox().inflate(Config.WITNESS_RANGE);
        List<UUID> seen = new ArrayList<>();
        long dayTime = level.getDayTime();

        for (Villager w : level.getEntitiesOfClass(Villager.class, area,
                v -> v != victim && v != killer && v.isAlive() && !v.isSleeping() && !PsychoManager.isKiller(v))) {
            boolean seesKiller = w.hasLineOfSight(killer);
            boolean seesVictim = w.hasLineOfSight(victim);
            if (!seesKiller && !seesVictim) {
                continue;
            }
            double dist = w.distanceTo(killer);
            if (dist > Config.WITNESS_RANGE || level.random.nextFloat() >= Config.WITNESS_CHANCE) {
                continue;
            }
            // Demasiado lejos: vio algo pero no puede identificarlo.
            boolean identified = seesKiller && dist <= Config.IDENTIFY_RANGE;

            CompoundTag m = new CompoundTag();
            m.putInt("Crime", crimeId);
            m.putBoolean("Identified", identified);
            m.putString("Killer", identified ? VillagerNames.nameOf(killer) : "");
            m.putString("Clothes", seesKiller ? Describe.clothes(killer) : "");
            m.putString("Escape", "");
            m.putString("Victim", victimName);
            m.putInt("X", victim.getBlockX());
            m.putInt("Y", victim.getBlockY());
            m.putInt("Z", victim.getBlockZ());
            m.putLong("Day", dayTime / 24000L);
            m.putInt("Time", (int) (dayTime % 24000L));

            ListTag list = w.getPersistentData().getList(KEY, Tag.TAG_COMPOUND);
            list.add(m);
            while (list.size() > Config.MAX_MEMORIES) {
                list.remove(0);
            }
            w.getPersistentData().put(KEY, list);

            seen.add(w.getUUID());
            startBehavior(level, w, killer);
        }

        if (!seen.isEmpty()) {
            PENDING.add(new PendingEscape(level, killer.getUUID(), seen, crimeId, killer.position()));
        }
    }

    private static void startBehavior(ServerLevel level, Villager witness, Villager killer) {
        Reaction type = Reaction.values()[level.random.nextInt(Reaction.values().length)];
        int ticks = switch (type) {
            case HIDE -> 600;
            case HELP -> 400;
            case FROZEN -> 200;
            case ESCAPE -> 800;
        };
        BEHAVIORS.put(witness.getUUID(), new Behavior(witness, type, killer.position(), ticks));
    }

    // ------------------------------------------------------------------ tick

    public static void tickLevel(ServerLevel level) {
        if (!PENDING.isEmpty()) {
            Iterator<PendingEscape> it = PENDING.iterator();
            while (it.hasNext()) {
                PendingEscape p = it.next();
                if (p.level != level) {
                    continue;
                }
                if (--p.ticks <= 0) {
                    finishEscape(p);
                    it.remove();
                }
            }
        }
        if (!BEHAVIORS.isEmpty()) {
            for (Behavior b : new ArrayList<>(BEHAVIORS.values())) {
                if (b.villager.level() != level) {
                    continue;
                }
                if (b.villager.isRemoved() || !b.villager.isAlive() || --b.ticks <= 0) {
                    BEHAVIORS.remove(b.villager.getUUID());
                    continue;
                }
                tickBehavior(level, b);
            }
        }
    }

    private static void finishEscape(PendingEscape p) {
        Entity e = p.level.getEntity(p.killerId);
        if (!(e instanceof Villager killer) || !killer.isAlive()) {
            return;
        }
        double dx = killer.getX() - p.origin.x;
        double dz = killer.getZ() - p.origin.z;
        if (Math.sqrt(dx * dx + dz * dz) < 3.0D) {
            return; // no se ha ido a ningun sitio: nadie puede decir hacia donde huyo
        }
        String direction = Describe.compass(dx, dz);
        for (UUID id : p.witnesses) {
            Entity we = p.level.getEntity(id);
            // Solo lo sabe quien todavia lo ve alejarse.
            if (we instanceof Villager w && w.isAlive() && w.distanceTo(killer) <= Config.WITNESS_RANGE
                    && w.hasLineOfSight(killer)) {
                setEscape(w, p.crimeId, direction);
            }
        }
    }

    private static void setEscape(Villager witness, int crimeId, String direction) {
        ListTag list = witness.getPersistentData().getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            if (m.getInt("Crime") == crimeId) {
                m.putString("Escape", direction);
            }
        }
    }

    private static void tickBehavior(ServerLevel level, Behavior b) {
        Villager w = b.villager;
        // Evita que el cerebro normal lo mande a otro sitio.
        w.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        switch (b.type) {
            case FROZEN -> {
                w.getNavigation().stop();
                Vec3 m = w.getDeltaMovement();
                w.setDeltaMovement(0.0D, m.y, 0.0D);
                if (w.tickCount % 10 == 0) {
                    level.sendParticles(ParticleTypes.SPLASH, w.getX(), w.getY() + 2.0D, w.getZ(),
                            3, 0.2D, 0.1D, 0.2D, 0.0D);
                }
            }
            case HIDE -> {
                if (w.tickCount % 20 == 0) {
                    BlockPos home = home(w);
                    if (home != null && w.distanceToSqr(Vec3.atCenterOf(home)) < 48.0D * 48.0D) {
                        Vec3 d = Vec3.atCenterOf(home);
                        if (w.distanceToSqr(d) > 4.0D) {
                            run(w, d, 0.75D);
                        } else {
                            w.getNavigation().stop();
                        }
                    } else {
                        run(w, awayFrom(w, b.threat, 16.0D), 0.75D);
                    }
                }
            }
            case HELP -> {
                if (w.tickCount % 20 == 0) {
                    Villager other = nearestOther(level, w, 32.0D);
                    if (other == null) {
                        run(w, awayFrom(w, b.threat, 16.0D), 0.75D);
                    } else if (w.distanceToSqr(other) > 9.0D) {
                        run(w, other.position(), 0.75D);
                    } else {
                        w.getNavigation().stop();
                        level.playSound(null, w.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    }
                }
            }
            case ESCAPE -> {
                if (w.tickCount % 20 == 0) {
                    run(w, awayFrom(w, b.threat, 24.0D), 0.8D);
                }
            }
        }
    }

    private static void run(Villager w, Vec3 dest, double speed) {
        w.getNavigation().moveTo(dest.x, dest.y, dest.z, speed);
    }

    private static Vec3 awayFrom(Villager w, Vec3 threat, double distance) {
        Vec3 dir = new Vec3(w.getX() - threat.x, 0.0D, w.getZ() - threat.z);
        if (dir.lengthSqr() < 0.01D) {
            dir = new Vec3(1.0D, 0.0D, 0.0D);
        }
        return w.position().add(dir.normalize().scale(distance));
    }

    private static BlockPos home(Villager w) {
        return w.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
    }

    private static Villager nearestOther(ServerLevel level, Villager self, double range) {
        Villager best = null;
        double bestDist = Double.MAX_VALUE;
        for (Villager v : level.getEntitiesOfClass(Villager.class, self.getBoundingBox().inflate(range),
                v -> v != self && v.isAlive() && !PsychoManager.isKiller(v))) {
            double d = self.distanceToSqr(v);
            if (d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return best;
    }
}
