package com.psycho.villager.entity;

import com.psycho.villager.Config;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Cadaver de un aldeano. Se dibuja tumbado en el suelo y con un tono rojo (sangre).
 * Cualquier jugador puede quitarlo pegandole.
 */
public class CorpseEntity extends Mob {
    private int age;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
        // deathTime > 0 hace que el renderer lo dibuje tumbado y con el overlay rojo.
        this.deathTime = 20;
    }

    @Override
    public void tick() {
        super.tick();
        this.deathTime = 20;
        if (!this.level().isClientSide) {
            this.age++;
            if (this.age > Config.CORPSE_LIFETIME) {
                this.discard();
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            this.discard();
            return true;
        }
        if (source.getEntity() instanceof Player) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.3F),
                        this.getX(), this.getY() + 0.3D, this.getZ(), 15, 0.4D, 0.2D, 0.4D, 0.0D);
            }
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CorpseAge", this.age);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.age = tag.getInt("CorpseAge");
        this.deathTime = 20;
    }
}
