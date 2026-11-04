package com.psycho.villager.registry;

import com.psycho.villager.PsychoVillagerMod;
import com.psycho.villager.block.BloodBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PsychoVillagerMod.MODID);

    public static final RegistryObject<Block> BLOOD = BLOCKS.register("blood_splatter",
            () -> new BloodBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .replaceable()
                    .pushReaction(PushReaction.DESTROY)
                    .sound(SoundType.WET_GRASS)));
}
