package com.psycho.villager.client;

import com.psycho.villager.PsychoVillagerMod;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Efectos visuales del cliente (bus de Forge). */
@Mod.EventBusSubscriber(modid = PsychoVillagerMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    /** Cuando el asesino entra por la ventana va agachado: se le dibuja mas bajo. */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof Villager villager && villager.isShiftKeyDown()) {
            event.getPoseStack().scale(1.0F, 0.78F, 1.0F);
        }
    }
}
