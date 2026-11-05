package com.psycho.villager.logic;

import com.psycho.villager.Config;
import com.psycho.villager.block.BloodBlock;
import com.psycho.villager.data.PsychoData;
import com.psycho.villager.entity.CorpseEntity;
import com.psycho.villager.entity.PlayerCorpseEntity;
import com.psycho.villager.registry.ModBlocks;
import com.psycho.villager.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        public int bloodyTicks;
        // Plan de la ventana: 0 = acercarse, 1 = mirar la ventana, 2 = romperla y entrar agachado.
        public WindowPlan window;
        public int windowStage;
        public int windowTicks;
        /** Jugadores a los que ya mato: no los vuelve a atacar hasta que ellos le peguen otra vez. */
        public final Set<UUID> spared = new HashSet<>();

        public KillerState(Villager villager) {
            this.villager = villager;
        }
    }

    /** Ventana por la que va a entrar: donde se pone fuera, la ventana y donde aparece dentro. */
    public record WindowPlan(BlockPos pos, Vec3 outside, Vec3 mid, Vec3 inside) {}

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
        KILLERS.computeIfAbsent(villager.getUUID(), id -> {
            KillerState state = new KillerState(villager);
            // Recupera a quien ya habia matado (se guarda en el propio aldeano).
            ListTag list = villager.getPersistentData().getList("PsychoSpared", 8);
            for (int i = 0; i < list.size(); i++) {
                try {
                    state.spared.add(UUID.fromString(list.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // nombre raro: se ignora
                }
            }
            return state;
        });
    }

    private static void saveSpared(KillerState state) {
        ListTag list = new ListTag();
        for (UUID id : state.spared) {
            list.add(StringTag.valueOf(id.toString()));
        }
        state.villager.getPersistentData().put("PsychoSpared", list);
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
        // Despues de matar, deja un rastro de sangre por donde camina.
        if (state.bloodyTicks > 0) {
            state.bloodyTicks--;
            if (killer.tickCount % 8 == 0) {
                splatAt(level, killer.blockPosition());
            }
        }

        boolean forced = state.forcedTicks > 0;
        if (forced) {
            state.forcedTicks--;
        }
        boolean hunting = forced || (night && state.nightKills < Config.MAX_KILLS_PER_NIGHT);

        if (!hunting) {
            state.target = null;
            cancelWindow(state);
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
            // A veces, en vez de ir directo, va primero a una ventana cercana a la victima.
            if (target != null && state.window == null && level.random.nextFloat() < Config.WINDOW_CHANCE) {
                WindowPlan plan = findWindow(level, killer, target);
                if (plan != null) {
                    startWindow(state, plan);
                }
            }
        }
        if (target == null) {
            cancelWindow(state);
            setWeapon(killer, false);
            return;
        }

        setWeapon(killer, true);
        if (killer.isSleeping()) {
            killer.stopSleeping();
        }
        // Evita que el cerebro normal del aldeano le mande a otro sitio.
        killer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        // Plan de la ventana: mientras dura, no hace nada mas.
        if (state.window != null && tickWindow(level, state)) {
            return;
        }

        if (killer.tickCount % 40 == 0) {
            heartbeat(level, killer);
        }
        killer.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double speed = target instanceof Player ? Config.SPEED_PLAYER : Config.SPEED_VILLAGER;
        if (killer.tickCount % 4 == 0) {
            killer.getNavigation().moveTo(target, speed);
        }
        if (killer.tickCount % 3 == 0) {
            openDoors(level, killer);
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

    // ------------------------------------------------------------------ ventana

    private static boolean isWindow(BlockState state) {
        Block block = state.getBlock();
        return block instanceof AbstractGlassBlock || block instanceof StainedGlassPaneBlock || block == Blocks.GLASS_PANE;
    }

    /** Sitio donde se puede estar de pie en esa columna (cerca de la altura de la ventana), o null. */
    private static Vec3 standSpot(ServerLevel level, BlockPos column, int windowY) {
        for (int y = windowY; y >= windowY - 3; y--) {
            BlockPos feet = new BlockPos(column.getX(), y, column.getZ());
            BlockPos below = feet.below();
            boolean free = level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
            if (free && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
            }
        }
        return null;
    }

    /** Busca la ventana mas cercana a la victima por la que se pueda entrar desde fuera. */
    private static WindowPlan findWindow(ServerLevel level, Villager killer, LivingEntity target) {
        BlockPos center = target.blockPosition();
        int r = Config.WINDOW_SEARCH_RADIUS;
        WindowPlan best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -3, -r), center.offset(r, 3, r))) {
            if (!level.hasChunkAt(pos) || !isWindow(level.getBlockState(pos))) {
                continue;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                Vec3 outside = standSpot(level, pos.relative(dir), pos.getY());
                Vec3 inside = standSpot(level, pos.relative(dir.getOpposite()), pos.getY());
                if (outside == null || inside == null) {
                    continue;
                }
                double distIn = inside.distanceToSqr(target.position());
                double distOut = outside.distanceToSqr(target.position());
                if (distIn >= distOut || distIn > (double) r * r || killer.distanceToSqr(outside) > 48.0D * 48.0D) {
                    continue;
                }
                if (distIn < bestDist) {
                    bestDist = distIn;
                    BlockPos fixed = pos.immutable();
                    best = new WindowPlan(fixed, outside,
                            new Vec3(fixed.getX() + 0.5D, fixed.getY(), fixed.getZ() + 0.5D), inside);
                }
            }
        }
        return best;
    }

    private static void startWindow(KillerState state, WindowPlan plan) {
        cancelWindow(state);
        state.window = plan;
        state.windowStage = 0;
        state.windowTicks = 0;
    }

    private static void cancelWindow(KillerState state) {
        if (state.window != null) {
            state.window = null;
            state.windowStage = 0;
            state.windowTicks = 0;
            state.villager.noPhysics = false;
            state.villager.setShiftKeyDown(false);
        }
    }

    /** Devuelve true mientras el asesino sigue ocupado con la ventana. */
    private static boolean tickWindow(ServerLevel level, KillerState state) {
        Villager killer = state.villager;
        WindowPlan plan = state.window;
        state.windowTicks++;

        switch (state.windowStage) {
            case 0 -> {
                // Acercarse a la ventana por fuera.
                if (state.windowTicks > 600) {
                    cancelWindow(state);
                    return false;
                }
                Vec3 out = plan.outside();
                if (state.windowTicks % 10 == 1) {
                    killer.getNavigation().moveTo(out.x, out.y, out.z, Config.SPEED_VILLAGER);
                }
                double dx = killer.getX() - out.x;
                double dz = killer.getZ() - out.z;
                if (dx * dx + dz * dz < 2.25D && Math.abs(killer.getY() - out.y) < 1.5D) {
                    killer.getNavigation().stop();
                    state.windowStage = 1;
                    state.windowTicks = 0;
                }
            }
            case 1 -> {
                // Se queda quieto mirando la ventana.
                killer.getNavigation().stop();
                killer.getLookControl().setLookAt(plan.mid().x, plan.mid().y + 1.0D, plan.mid().z);
                if (state.windowTicks >= Config.WINDOW_LOOK_TICKS) {
                    breakWindow(level, plan.pos());
                    killer.setShiftKeyDown(true);
                    killer.noPhysics = true;
                    state.windowStage = 2;
                    state.windowTicks = 0;
                }
            }
            case 2 -> {
                // Entra agachado: sube a la ventana y baja dentro de la casa.
                int t = state.windowTicks;
                Vec3 from;
                Vec3 to;
                double f;
                if (t <= 12) {
                    from = plan.outside();
                    to = plan.mid();
                    f = t / 12.0D;
                } else {
                    from = plan.mid();
                    to = plan.inside();
                    f = (t - 12) / 12.0D;
                }
                Vec3 p = from.lerp(to, Math.min(f, 1.0D));
                Vec3 dir = plan.inside().subtract(plan.outside());
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * 57.29577951308232D) - 90.0F;
                killer.noPhysics = true;
                killer.setDeltaMovement(Vec3.ZERO);
                killer.getNavigation().stop();
                killer.moveTo(p.x, p.y, p.z, yaw, 0.0F);
                killer.yBodyRot = yaw;
                killer.yHeadRot = yaw;
                if (t >= 24) {
                    // Ya esta dentro: se levanta y sigue a por la victima.
                    cancelWindow(state);
                    return false;
                }
            }
            default -> {
                cancelWindow(state);
                return false;
            }
        }
        return true;
    }

    /** Rompe la ventana (y la de encima si tambien es cristal). */
    private static void breakWindow(ServerLevel level, BlockPos pos) {
        level.destroyBlock(pos, false);
        BlockPos above = pos.above();
        if (isWindow(level.getBlockState(above))) {
            level.destroyBlock(above, false);
        }
    }

    // ------------------------------------------------------------------ ambiente

    /** Latido cerca de los jugadores cuando el asesino esta cazando. */
    private static void heartbeat(ServerLevel level, Villager killer) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(killer) < 20.0D * 20.0D) {
                level.playSound(null, killer.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 1.0F);
                return;
            }
        }
    }

    /** Una mancha de sangre suelta en el suelo (rastro). */
    private static void splatAt(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(pos).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            level.setBlock(pos, ModBlocks.BLOOD.get().defaultBlockState(), 3);
        }
    }

    /** El asesino abre las puertas de madera que tiene al lado mientras caza. */
    private static void openDoors(ServerLevel level, Villager killer) {
        BlockPos base = killer.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 1, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door
                    && state.is(BlockTags.WOODEN_DOORS)
                    && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !state.getValue(DoorBlock.OPEN)) {
                door.setOpen(killer, level, state, pos.immutable(), true);
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
                if (!validPlayer(player) || state.spared.contains(player.getUUID())) {
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

    /** Usado por /psycho window attack: va a una ventana, la rompe, entra agachado y ataca. */
    public static boolean forceWindowAttack(KillerState state) {
        if (!(state.villager.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity target = findTarget(level, state, true);
        if (target == null) {
            return false;
        }
        WindowPlan plan = findWindow(level, state.villager, target);
        if (plan == null) {
            return false;
        }
        startWindow(state, plan);
        state.target = target;
        state.forcedTicks = 1800;
        state.cooldown = 0;
        return true;
    }

    /** Un jugador golpea al asesino: se defiende y le persigue un rato. */
    public static void onKillerHurtByPlayer(KillerState state, Player player) {
        // Si le pegas otra vez, vuelve a poder atacarte.
        if (state.spared.remove(player.getUUID())) {
            saveSpared(state);
        }
        state.revengeId = player.getUUID();
        state.forcedTicks = 600;
        state.target = null;
        state.searchCooldown = 0;
    }

    // ------------------------------------------------------------------ muertes

    /** Un aldeano muere asesinado por el asesino. */
    public static void onMurder(ServerLevel level, Villager victim, Villager killer) {
        if (level.random.nextFloat() < Config.SCREAM_CHANCE) {
            scream(level, victim);
        }
        boolean hide = level.random.nextFloat() < Config.HIDE_BODY_CHANCE;
        leaveBody(level, victim, hide);
        maybeDropList(level, killer, victim);
        KillerState state = getState(killer);
        if (state != null) {
            state.bloodyTicks = Config.BLOOD_TRAIL_TICKS;
        }
    }

    /** Un jugador muere (por lo que sea): ningun asesino sigue vengandose de el. */
    public static void onPlayerDied(ServerPlayer player) {
        for (KillerState state : KILLERS.values()) {
            if (player.getUUID().equals(state.revengeId)) {
                state.revengeId = null;
                state.forcedTicks = 0;
            }
            if (state.target == player) {
                state.target = null;
            }
        }
    }

    /** El asesino mata a un jugador: queda su cuerpo con su skin en el suelo. */
    public static void onPlayerKilled(ServerLevel level, ServerPlayer player, Villager killer) {
        level.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F),
                player.getX(), player.getY() + 0.6D, player.getZ(), 45, 0.4D, 0.5D, 0.4D, 0.02D);

        PlayerCorpseEntity corpse = ModEntities.PLAYER_CORPSE.get().create(level);
        if (corpse != null) {
            float yaw = player.yBodyRot;
            corpse.moveTo(player.getX(), player.getY(), player.getZ(), yaw, 0.0F);
            corpse.setYRot(yaw);
            corpse.yBodyRot = yaw;
            corpse.yBodyRotO = yaw;
            corpse.yHeadRot = yaw;
            corpse.yHeadRotO = yaw;
            corpse.setOwner(player.getUUID());
            corpse.setCustomName(player.getName());
            level.addFreshEntity(corpse);
        }
        placeBlood(level, player.blockPosition(), level.random);
        maybeDropList(level, killer, null);

        // No vuelve a intentar matar a este jugador hasta que le pegue otra vez.
        KillerState state = getState(killer);
        if (state != null) {
            state.spared.add(player.getUUID());
            saveSpared(state);
            state.target = null;
            state.forcedTicks = 0;
            cancelWindow(state);
            state.bloodyTicks = Config.BLOOD_TRAIL_TICKS;
        }
    }

    /** A veces el asesino suelta una lista con los nombres de los aldeanos. */
    private static void maybeDropList(ServerLevel level, Villager killer, Villager exclude) {
        if (level.random.nextFloat() >= Config.LIST_DROP_CHANCE) {
            return;
        }
        AABB area = killer.getBoundingBox().inflate(Config.HUNT_RANGE);
        List<String> names = new ArrayList<>();
        for (Villager villager : level.getEntitiesOfClass(Villager.class, area,
                v -> v != exclude && v.isAlive() && v.hasCustomName()
                        && (Config.LIST_INCLUDES_KILLER || !isKiller(v)))) {
            names.add(villager.getCustomName().getString());
        }
        if (names.isEmpty()) {
            return;
        }

        ListTag pages = new ListTag();
        for (int start = 0; start < names.size(); start += 12) {
            StringBuilder text = new StringBuilder("Lista de nombres\n\n");
            for (int i = start; i < Math.min(start + 12, names.size()); i++) {
                text.append("- ").append(names.get(i)).append('\n');
            }
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(text.toString()))));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Lista de nombres");
        tag.putString("author", "???");
        tag.put("pages", pages);

        ItemEntity drop = new ItemEntity(level, killer.getX(), killer.getY() + 0.5D, killer.getZ(), book);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    /** El asesino muere. */
    public static void onKillerDeath(ServerLevel level, Villager killer, Entity cause) {
        PsychoData.get(level).setKillerChosen(true);
        leaveBody(level, killer, false);
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
        Component hint = Component.literal("¡Se oye el grito de " + VillagerNames.nameOf(victim) + "!")
                .withStyle(ChatFormatting.RED);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(victim) < 48.0D * 48.0D) {
                player.displayClientMessage(hint, true);
            }
        }
    }

    /** Quita al aldeano y deja su cadaver y sangre en el suelo. */
    private static void leaveBody(ServerLevel level, Villager victim, boolean tryHide) {
        level.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.5F),
                victim.getX(), victim.getY() + 0.6D, victim.getZ(), 45, 0.4D, 0.5D, 0.4D, 0.02D);

        BlockPos deathPos = victim.blockPosition();
        if (!victim.isBaby()) {
            CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
            if (corpse != null) {
                double x = victim.getX();
                double y = victim.getY();
                double z = victim.getZ();
                float yaw = victim.yBodyRot;
                boolean hidden = false;
                if (tryHide) {
                    BedSpot spot = findBed(level, deathPos);
                    if (spot != null) {
                        x = spot.x();
                        y = spot.y();
                        z = spot.z();
                        yaw = spot.yaw();
                        hidden = true;
                    }
                }
                corpse.moveTo(x, y, z, yaw, 0.0F);
                corpse.setYRot(yaw);
                corpse.yBodyRot = yaw;
                corpse.yBodyRotO = yaw;
                corpse.yHeadRot = yaw;
                corpse.yHeadRotO = yaw;
                corpse.setHiddenBody(hidden);
                level.addFreshEntity(corpse);
            }
        }
        placeBlood(level, deathPos, level.random);
        victim.discard();
    }

    private record BedSpot(double x, double y, double z, float yaw) {}

    /** Busca la cama mas cercana (cargada) para esconder el cuerpo debajo. */
    private static BedSpot findBed(ServerLevel level, BlockPos center) {
        int r = Config.HIDE_BODY_RADIUS;
        BedSpot best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                double dist = pos.distSqr(center);
                if (dist < bestDist) {
                    bestDist = dist;
                    Direction facing = state.getValue(BedBlock.FACING);
                    best = new BedSpot(pos.getX() + 0.5D + facing.getStepX() * 0.5D, pos.getY(),
                            pos.getZ() + 0.5D + facing.getStepZ() * 0.5D, facing.toYRot() + 90.0F);
                }
            }
        }
        return best;
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
