package com.psycho.villager.registry;

import com.psycho.villager.PsychoVillagerMod;
import com.psycho.villager.entity.CorpseEntity;
import com.psycho.villager.entity.PlayerCorpseEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, PsychoVillagerMod.MODID);

    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITIES.register("villager_corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .clientTrackingRange(8)
                    .build("villager_corpse"));

    public static final RegistryObject<EntityType<PlayerCorpseEntity>> PLAYER_CORPSE = ENTITIES.register("player_corpse",
            () -> EntityType.Builder.<PlayerCorpseEntity>of(PlayerCorpseEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .clientTrackingRange(8)
                    .build("player_corpse"));
}
