package com.psycho.villager;

import com.psycho.villager.registry.ModBlocks;
import com.psycho.villager.registry.ModEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Psycho Villager: un aldeano es un asesino y mata a otro aldeano cada noche.
 */
@Mod(PsychoVillagerMod.MODID)
public class PsychoVillagerMod {
    public static final String MODID = "psychovillager";

    public PsychoVillagerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
    }
}
