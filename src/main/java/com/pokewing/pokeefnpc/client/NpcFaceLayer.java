package com.pokewing.pokeefnpc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.pokewing.pokeefnpc.compat.PokeFaceBridge;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Puts the NPC's face on its head.
 *
 * <p>The pose stack is moved onto the head bone before drawing, exactly as
 * PokeFace's own player layer does, so the face inherits whatever the head is
 * doing — the walk cycle, the look-at, an Epic Fight animation — rather than
 * being placed at a guessed offset.
 *
 * <p>Skipped entirely when PokeFace is not installed. The mood system underneath
 * keeps running either way; without PokeFace it simply shows in behaviour and
 * emotes rather than on a face.
 */
@OnlyIn(Dist.CLIENT)
public class NpcFaceLayer extends RenderLayer<NpcEntity, NpcModel> {

    public NpcFaceLayer(RenderLayerParent<NpcEntity, NpcModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light,
                       NpcEntity npc, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (npc.isInvisible() || !PokeFaceBridge.isAvailable()) {
            return;
        }
        ModelPart head = getParentModel().head;
        poseStack.pushPose();
        head.translateAndRotate(poseStack);
        PokeFaceBridge.renderFace(poseStack, buffers, npc, light, npc.tickCount);
        poseStack.popPose();
    }
}
