package com.psycho.villager;

import com.psycho.villager.registry.ModEntities;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del bus del mod (registro). */
@Mod.EventBusSubscriber(modid = PsychoVillagerMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.CORPSE.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.PLAYER_CORPSE.get(), Mob.createMobAttributes().build());
    }
}
