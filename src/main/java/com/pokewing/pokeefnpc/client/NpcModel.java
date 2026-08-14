package com.pokewing.pokeefnpc.client;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The NPC body, built on the vanilla player model.
 *
 * <p>Using {@link PlayerModel} rather than a bespoke one is what makes the rest
 * of this work: NPCs take ordinary player skins, PokeFace's face renderer finds
 * the head bone exactly where it expects it, and Epic Fight — which already knows
 * how to drive a biped rig — has nothing unusual to deal with.
 *
 * <p>{@link #setupAnim} runs the standard walk and look animation first and then
 * layers the emote on top, so a smith can hammer while turning to watch you go
 * past instead of the gesture replacing the whole pose.
 */
@OnlyIn(Dist.CLIENT)
public class NpcModel extends PlayerModel<NpcEntity> {

    public NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(NpcEntity npc, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        Emote emote = npc.currentEmote();
        if (emote == null) {
            return;
        }
        // Epic Fight, when installed, drives the rig itself for emotes that have
        // an animation. Posing here as well would fight it for control of the
        // same bones.
        if (com.pokewing.pokeefnpc.compat.EpicFightBridge.isLoaded()
                && emote.epicFightAnimation() != null) {
            return;
        }

        float phase = NpcEmoteAnimator.phaseOf(npc, emote, ageInTicks);
        NpcEmoteAnimator.apply(this, emote, phase, limbSwingAmount);
    }

    /**
     * How strongly the emote overrides the walk cycle. A gesture is faded out
     * while the NPC is actually moving, because somebody striding across a square
     * should not also be sweeping a floor.
     */
    static float blendFor(float limbSwingAmount) {
        return Mth.clamp(1.0F - limbSwingAmount * 1.5F, 0.0F, 1.0F);
    }
}
