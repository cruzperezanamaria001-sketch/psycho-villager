package com.psycho.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.psycho.villager.entity.PlayerCorpseEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/** Dibuja el cuerpo del jugador tumbado boca arriba, con su skin. */
public class PlayerCorpseRenderer extends MobRenderer<PlayerCorpseEntity, PlayerModel<PlayerCorpseEntity>> {

    public PlayerCorpseRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(PlayerCorpseEntity entity) {
        Optional<UUID> owner = entity.getOwner();
        if (owner.isPresent()) {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                PlayerInfo info = connection.getPlayerInfo(owner.get());
                if (info != null) {
                    return info.getSkinLocation();
                }
            }
            return DefaultPlayerSkin.getDefaultSkin(owner.get());
        }
        return DefaultPlayerSkin.getDefaultSkin(entity.getUUID());
    }

    @Override
    protected void setupRotations(PlayerCorpseEntity entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        // Tumbado boca arriba.
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        // Centra el cuerpo en la posicion de la entidad y lo sube para que no se hunda en el suelo.
        poseStack.translate(0.0D, -0.9D, 0.2D);
    }
}
