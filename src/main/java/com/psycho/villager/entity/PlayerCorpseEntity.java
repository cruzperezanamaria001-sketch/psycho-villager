package com.psycho.villager.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** Cuerpo de un jugador que murio a manos del asesino. Se dibuja con la skin del jugador. */
public class PlayerCorpseEntity extends CorpseEntity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(PlayerCorpseEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public PlayerCorpseEntity(EntityType<? extends PlayerCorpseEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(OWNER, Optional.empty());
    }

    public void setOwner(UUID owner) {
        this.entityData.set(OWNER, Optional.of(owner));
    }

    public Optional<UUID> getOwner() {
        return this.entityData.get(OWNER);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.getOwner().ifPresent(id -> tag.putUUID("CorpseOwner", id));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("CorpseOwner")) {
            this.setOwner(tag.getUUID("CorpseOwner"));
        }
    }
}
