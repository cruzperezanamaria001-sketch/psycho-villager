package com.psycho.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.psycho.villager.entity.CorpseEntity;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Dibuja el cadaver con el modelo del aldeano, tumbado en el suelo. */
public class CorpseRenderer extends MobRenderer<CorpseEntity, VillagerModel<CorpseEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/entity/villager/villager.png");

    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(CorpseEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        // Centra el cuerpo tumbado sobre la posicion de la entidad y lo sube un poco para que no se hunda.
        poseStack.translate(0.27D, -0.95D, 0.0D);
    }
}
