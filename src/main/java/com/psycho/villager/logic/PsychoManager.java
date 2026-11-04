package com.psycho.villager.logic;

import com.psycho.villager.Config;
import com.psycho.villager.block.BloodBlock;
import com.psycho.villager.data.PsychoData;
import com.psycho.villager.entity.CorpseEntity;
import com.psycho.villager.registry.ModBlocks;
import com.psycho.villager.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Toda la logica del aldeano asesino: elegirlo, hacerlo cazar de noche, atacar,
 * y lo que pasa cuando alguien muere (grito, cadaver, sangre).
 */
public final class PsychoManager {
    private PsychoManager() {}

    public static final String KILLER_TAG = "PsychoKiller";

    /** Estado de cada asesino cargado en el mundo. */
    public static final class KillerState {
        public final Villager villager;
        public LivingEntity target;
        public UUID revengeId;
        public long day = -1L;
        public int nightKills;
        public int cooldown;
        public int forcedTicks;
        public int searchCooldown;
        public int stuckTicks;
        public double lastX;
        public double lastZ;

        public KillerState(Villager villager) {
            this.villager = villager;
        }
    }

    private static final Map<UUID, KillerState> KILLERS = new LinkedHashMap<>();
    private static long lastAutoDay = -1L;

    // ------------------------------------------------------------------ utilidades

    public static boolean isKiller(Entity entity) {
        return entity instanceof Villager && entity.getPersistentData().getBoolean(KILLER_TAG);
    }

    public static KillerState getState(Entity entity) {
        return KILLERS.get(entity.getUUID());
    }

    public static List<KillerState> allKillers() {
        return new ArrayList<>(KILLERS.values());
    }

    public static void register(Villager villager) {
        KILLERS.computeIfAbsent(villager.getUUID(), id -> new KillerState(villager));
    }

    public static void unregister(Entity entity) {
        KILLERS.remove(entity.getUUID());
    }

    public static void reset() {
        KILLERS.clear();
        lastAutoDay = -1L;
    }

    /** Convierte a un aldeano en el asesino. */
    public static void makeKiller(Villager villager) {
        villager.getPersistentData().putBoolean(KILLER_TAG, true);
        AttributeInstance health = villager.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Config.KILLER_HEALTH);
        }
        villager.setHealth(villager.getMaxHealth());
        villager.setPersistenceRequired();
        villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        if (villager.level() instanceof ServerLevel level) {
            PsychoData.get(level).setKillerChosen(true);
            register(villager);
        }
    }

    // ------------------------------------------------------------------ tick

    public static void tickLevel(ServerLevel level) {
        boolean overworld = level.dimension() == Level.OVERWORLD;
        long time = level.getDayTime();
        long timeOfDay = time % 24000L;
        long day = time / 24000L;
        boolean night = overworld && timeOfDay >= Config.NIGHT_START && timeOfDay < Config.NIGHT_END;

        if (night && Config.AUTO_CHOOSE_KILLER && day != lastAutoDay) {
            lastAutoDay = day;
            autoChoose(level);
        }

        if (KILLERS.isEmpty()) {
            return;
        }
        for (KillerState state : new ArrayList<>(KILLERS.values())) {
            Villager killer = state.villager;
            if (killer.level() != level) {
                continue;
            }
            if (killer.isRemoved() || !killer.isAlive()) {
                KILLERS.remove(killer.getUUID());
                continue;
            }
            tickKiller(level, state, night, day);
        }
    }

    private static void autoChoose(ServerLevel level) {
        if (PsychoData.get(level).isKillerChosen()) {
            return;
        }
        List<? extends Villager> villagers = level.getEntities(EntityTypeTest.forClass(Villager.class),
                v -> v.isAlive() && !v.isBaby());
        if (villagers.size() < 2) {
            return;
        }
        Villager pick = villagers.get(level.random.nextInt(villagers.size()));
        makeKiller(pick);
    }

    private static void tickKiller(ServerLevel level, KillerState state, boolean night, long day) {
        Villager killer = state.villager;

        if (state.day != day) {
            state.day = day;
            state.nightKills = 0;
        }
        if (state.cooldown > 0) {
            state.cooldown--;
        }
        if (state.searchCooldown > 0) {
            state.searchCooldown--;
        }

        boolean forced = state.forcedTicks > 0;
        if (forced) {
            state.forcedTicks--;
        }
        boolean hunting = forced || (night && state.nightKills < Config.MAX_KILLS_PER_NIGHT);

        if (!hunting) {
            state.target = null;
            setWeapon(killer, false);
            return;
        }

        // Validar objetivo actual.
        LivingEntity target = state.target;
        if (target != null && (target.isRemoved() || !target.isAlive() || target.level() != level)) {
            target = null;
            state.target = null;
        }
        if (target == null && state.searchCooldown <= 0) {
            state.searchCooldown = 30;
            target = findTarget(level, state, forced);
            state.target = target;
        }
        if (target == null) {
            setWeapon(killer, false);
            return;
        }

        setWeapon(killer, true);
        if (killer.isSleeping()) {
            killer.stopSleeping();
        }
        // Evita que el cerebro normal del aldeano le mande a otro sitio.
        killer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        killer.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double speed = target instanceof Player ? Config.SPEED_PLAYER : Config.SPEED_VILLAGER;
        if (killer.tickCount % 4 == 0) {
            killer.getNavigation().moveTo(target, speed);
        }

        if (killer.tickCount % 20 == 0) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, killer.getX(), killer.getY() + 2.1D, killer.getZ(),
                    1, 0.2D, 0.1D, 0.2D, 0.0D);
        }

        boolean inReach = killer.getBoundingBox().inflate(0.7D, 0.4D, 0.7D).intersects(target.getBoundingBox());

        // Si se queda atascado mucho tiempo, aparece junto a la victima.
        if (!inReach && killer.tickCount % 10 == 0) {
            double moved = Math.abs(killer.getX() - state.lastX) + Math.abs(killer.getZ() - state.lastZ);
            state.stuckTicks = moved < 0.2D ? state.stuckTicks + 10 : 0;
            state.lastX = killer.getX();
            state.lastZ = killer.getZ();
            if (state.stuckTicks >= 100) {
                state.stuckTicks = 0;
                killer.getNavigation().stop();
                killer.moveTo(target.getX(), target.getY(), target.getZ(), killer.getYRot(), killer.getXRot());
            }
        }

        if (inReach && state.cooldown <= 0) {
            strike(level, state, target, forced);
        }
    }

    private static void strike(ServerLevel level, KillerState state, LivingEntity target, boolean forced) {
        Villager killer = state.villager;
        killer.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, killer.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 0.8F);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.6F, 0.0F, 0.0F), 1.2F),
                target.getX(), target.getY() + target.getBbHeight() * 0.6D, target.getZ(),
                12, 0.25D, 0.3D, 0.25D, 0.0D);

        if (target instanceof Player) {
            target.hurt(level.damageSources().mobAttack(killer), Config.PLAYER_DAMAGE);
            state.cooldown = Config.PLAYER_HIT_COOLDOWN;
            if (!target.isAlive()) {
                state.target = null;
                state.forcedTicks = 0;
            }
        } else {
            target.hurt(level.damageSources().mobAttack(killer), 1000.0F);
            state.cooldown = 40;
            if (target.isRemoved() || !target.isAlive() || target.isDeadOrDying()) {
                state.target = null;
                if (forced) {
                    state.forcedTicks = 0;
                } else {
                    state.nightKills++;
                }
            }
        }
    }

    private static void setWeapon(Villager killer, boolean armed) {
        ItemStack hand = killer.getItemBySlot(EquipmentSlot.MAINHAND);
        if (armed) {
            if (hand.isEmpty()) {
                killer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                killer.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
        } else if (hand.is(Items.IRON_SWORD)) {
            killer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ objetivos

    private static boolean validPlayer(Player player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator();
    }

    public static LivingEntity findTarget(ServerLevel level, KillerState state, boolean includePlayers) {
        Villager killer = state.villager;

        if (includePlayers && state.revengeId != null) {
            Player revenge = level.getPlayerByUUID(state.revengeId);
            if (revenge != null && validPlayer(revenge)
                    && revenge.distanceToSqr(killer) < Config.HUNT_RANGE * Config.HUNT_RANGE) {
                return revenge;
            }
        }

        AABB area = killer.getBoundingBox().inflate(Config.HUNT_RANGE);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Villager villager : level.getEntitiesOfClass(Villager.class, area,
                v -> v != killer && v.isAlive() && !isKiller(v))) {
            double d = killer.distanceToSqr(villager);
            if (d < bestDist) {
                bestDist = d;
                best = villager;
            }
        }
        if (includePlayers) {
            for (ServerPlayer player : level.players()) {
                if (!validPlayer(player)) {
                    continue;
                }
                double d = killer.distanceToSqr(player);
                if (d < bestDist && d < Config.HUNT_RANGE * Config.HUNT_RANGE) {
                    bestDist = d;
                    best = player;
                }
            }
        }
        return best;
    }

    /** Usado por /psycho attack: ataca ahora al aldeano/jugador mas cercano. Devuelve false si no hay objetivo. */
    public static boolean forceAttack(KillerState state) {
        if (!(state.villager.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = findTarget(level, state, true);
        if (target == null) {
            return false;
        }
        state.target = target;
        state.forcedTicks = 1200;
        state.cooldown = 0;
        return true;
    }

    /** Un jugador golpea al asesino: se defiende y le persigue un rato. */
    public static void onKillerHurtByPlayer(KillerState state, Player player) {
        state.revengeId = player.getUUID();
        state.forcedTicks = 600;
        state.target = null;
        state.searchCooldown = 0;
    }

    // ------------------------------------------------------------------ muertes

    /** Un aldeano muere asesinado por el asesino. */
    public static void onMurder(ServerLevel level, Villager victim) {
        if (level.random.nextFloat() < Config.SCREAM_CHANCE) {
            scream(level, victim);
        }
        leaveBody(level, victim);
    }

    /** El asesino muere. */
    public static void onKillerDeath(ServerLevel level, Villager killer, Entity cause) {
        PsychoData.get(level).setKillerChosen(true);
        leaveBody(level, killer);
        Component message;
        if (cause instanceof Player player) {
            message = Component.literal("¡" + player.getName().getString() + " ha matado al aldeano asesino!")
                    .withStyle(ChatFormatting.GREEN);
        } else {
            message = Component.literal("¡El aldeano asesino ha muerto!").withStyle(ChatFormatting.GREEN);
        }
        level.getServer().getPlayerList().broadcastSystemMessage(message, false);
    }

    private static void scream(ServerLevel level, Villager victim) {
        BlockPos pos = victim.blockPosition();
        level.playSound(null, pos, SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 4.0F, 0.6F);
        level.playSound(null, pos, SoundEvents.VILLAGER_DEATH, SoundSource.NEUTRAL, 4.0F, 1.8F);
        level.playSound(null, pos, SoundEvents.GHAST_SCREAM, SoundSource.NEUTRAL, 2.0F, 1.6F);
        Component hint = Component.literal("¡Se oye el grito de un aldeano!").withStyle(ChatFormatting.RED);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(victim) < 48.0D * 48.0D) {
                player.displayClientMessage(hint, true);
            }
        }
    }

    /** Quita al aldeano y deja su cadaver y sangre en el suelo. */
    private static void leaveBody(ServerLevel level, Villager victim) {
        level.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F),
                victim.getX(), victim.getY() + 0.6D, victim.getZ(), 45, 0.4D, 0.5D, 0.4D, 0.02D);

        if (!victim.isBaby()) {
            CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
            if (corpse != null) {
                float yaw = victim.yBodyRot;
                corpse.moveTo(victim.getX(), victim.getY(), victim.getZ(), yaw, 0.0F);
                corpse.setYRot(yaw);
                corpse.yBodyRot = yaw;
                corpse.yBodyRotO = yaw;
                corpse.yHeadRot = yaw;
                corpse.yHeadRotO = yaw;
                level.addFreshEntity(corpse);
            }
        }
        placeBlood(level, victim.blockPosition(), level.random);
        victim.discard();
    }

    private static void placeBlood(ServerLevel level, BlockPos center, RandomSource random) {
        BlockState blood = ModBlocks.BLOOD.get().defaultBlockState();
        for (int i = 0; i < Config.BLOOD_SPLATTERS; i++) {
            int dx = random.nextInt(5) - 2;
            int dz = random.nextInt(5) - 2;
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = center.offset(dx, dy, dz);
                BlockPos below = pos.below();
                boolean freeSpot = level.getBlockState(pos).isAir() || level.getBlockState(pos).getBlock() instanceof BloodBlock;
                if (freeSpot && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                    level.setBlock(pos, blood, 3);
                    break;
                }
            }
        }
    }
}
