package com.pokewing.pokeefnpc.client;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Procedural gestures — every emote, drawn without Epic Fight.
 *
 * <p>This is what makes the emote system stand on its own. Epic Fight animations
 * are strictly an upgrade: where one exists it is used, and where it does not —
 * or where Epic Fight is not installed at all — the same gesture is produced here
 * from sines and the model's own bones. A vanilla install still sees the smith
 * hammering, the beggar pleading and the guard scanning the horizon.
 *
 * <p>Angles are in radians on the vanilla biped rig, where an arm at rest hangs
 * with {@code xRot = 0} and swings forward as {@code xRot} goes negative.
 */
@OnlyIn(Dist.CLIENT)
public final class NpcEmoteAnimator {

    private NpcEmoteAnimator() {
    }

    /**
     * Where in the gesture's cycle this NPC is, in [0,1).
     *
     * <p>Offset by the entity id so a row of guards is not perfectly in step —
     * synchronised idle animation is the single thing that most makes a crowd
     * look like clones.
     */
    public static float phaseOf(NpcEntity npc, Emote emote, float ageInTicks) {
        float period = Math.max(1.0F, emote.durationTicks());
        float offset = (npc.getId() % 17) * 3.0F;
        return ((ageInTicks + offset) % period) / period;
    }

    /** Poses the model for one frame of a gesture. */
    public static void apply(PlayerModel<NpcEntity> model, Emote emote, float phase,
                             float limbSwingAmount) {
        float blend = NpcModel.blendFor(limbSwingAmount);
        if (blend <= 0.01F) {
            return;
        }
        // A full sine over the cycle, and a half sine that only goes one way.
        float wave = Mth.sin(phase * Mth.TWO_PI);
        float swell = Mth.sin(phase * Mth.PI);

        ModelPart rightArm = model.rightArm;
        ModelPart leftArm = model.leftArm;
        ModelPart head = model.head;
        ModelPart body = model.body;

        switch (emote.pose()) {
            case WAVE -> {
                lerpArm(rightArm, blend, -2.6F + wave * 0.25F, 0.0F, -0.45F + wave * 0.35F);
                lerpRot(head, blend, head.xRot, head.yRot, wave * 0.08F);
            }
            case BOW -> {
                lerpRot(body, blend * swell, 0.5F, body.yRot, 0.0F);
                lerpRot(head, blend * swell, 0.4F, head.yRot, 0.0F);
                lerpArm(rightArm, blend * swell, -0.5F, 0.0F, 0.9F);
                lerpArm(leftArm, blend * swell, -0.3F, 0.0F, -0.5F);
            }
            case SALUTE -> {
                lerpArm(rightArm, blend, -2.2F, 0.0F, -1.1F);
                lerpArm(leftArm, blend, 0.0F, 0.0F, 0.1F);
                lerpRot(body, blend, -0.05F, body.yRot, 0.0F);
            }
            case NOD -> lerpRot(head, blend, 0.25F + wave * 0.25F, head.yRot, 0.0F);
            case SHAKE_HEAD -> lerpRot(head, blend, head.xRot, head.yRot + wave * 0.45F, 0.0F);
            case CLAP -> {
                float clap = Math.abs(wave);
                lerpArm(rightArm, blend, -1.4F, 0.0F, -0.55F - clap * 0.35F);
                lerpArm(leftArm, blend, -1.4F, 0.0F, 0.55F + clap * 0.35F);
            }
            case CHEER -> {
                float lift = 0.25F + Math.abs(wave) * 0.35F;
                lerpArm(rightArm, blend, -2.9F - lift, 0.0F, -0.3F);
                lerpArm(leftArm, blend, -2.9F - lift, 0.0F, 0.3F);
                lerpRot(head, blend, -0.25F, head.yRot, 0.0F);
            }
            case LAUGH -> {
                lerpRot(body, blend, wave * 0.12F - 0.1F, body.yRot, 0.0F);
                lerpRot(head, blend, -0.3F + wave * 0.12F, head.yRot, 0.0F);
                lerpArm(rightArm, blend, -0.9F, 0.0F, -0.7F);
            }
            case POINT -> {
                lerpArm(rightArm, blend, -1.6F, 0.0F, -0.15F);
                lerpRot(head, blend, head.xRot, head.yRot, 0.0F);
            }
            case BECKON -> lerpArm(rightArm, blend, -1.5F + wave * 0.35F, 0.0F, -0.4F);
            case SHRUG -> {
                float lift = swell * 0.5F;
                lerpArm(rightArm, blend, -0.3F, 0.0F, -1.1F - lift);
                lerpArm(leftArm, blend, -0.3F, 0.0F, 1.1F + lift);
                lerpRot(head, blend, -0.12F, head.yRot, 0.0F);
            }
            case FACEPALM -> {
                lerpArm(rightArm, blend * swell, -2.5F, 0.0F, -0.35F);
                lerpRot(head, blend * swell, 0.45F, head.yRot, 0.0F);
            }
            case THINK -> {
                lerpArm(rightArm, blend, -2.1F, 0.0F, -0.5F);
                lerpArm(leftArm, blend, -0.6F, 0.0F, 0.9F);
                lerpRot(head, blend, 0.15F, head.yRot + wave * 0.06F, 0.0F);
            }
            case CROSS_ARMS -> {
                lerpArm(rightArm, blend, -1.5F, 0.35F, -0.85F);
                lerpArm(leftArm, blend, -1.5F, -0.35F, 0.85F);
            }
            case JEER -> {
                lerpArm(rightArm, blend, -1.3F + wave * 0.4F, 0.0F, -0.3F);
                lerpRot(head, blend, -0.15F, head.yRot, wave * 0.1F);
                lerpRot(body, blend, 0.0F, body.yRot + wave * 0.08F, 0.0F);
            }
            case WEEP -> {
                lerpArm(rightArm, blend, -2.4F, 0.0F, -0.3F);
                lerpArm(leftArm, blend, -2.4F, 0.0F, 0.3F);
                lerpRot(head, blend, 0.5F, head.yRot, 0.0F);
                lerpRot(body, blend, 0.15F + Math.abs(wave) * 0.05F, body.yRot, 0.0F);
            }
            case BEG -> {
                float reach = 0.15F + Math.abs(wave) * 0.2F;
                lerpArm(rightArm, blend, -1.4F - reach, 0.0F, -0.25F);
                lerpArm(leftArm, blend, -1.4F - reach, 0.0F, 0.25F);
                lerpRot(head, blend, 0.3F, head.yRot, 0.0F);
                lerpRot(body, blend, 0.2F, body.yRot, 0.0F);
            }
            case SHIVER -> {
                float shake = wave * 0.06F;
                lerpArm(rightArm, blend, -0.35F + shake, 0.0F, -0.55F);
                lerpArm(leftArm, blend, -0.35F - shake, 0.0F, 0.55F);
                lerpRot(head, blend, 0.15F, head.yRot + shake, shake);
                lerpRot(body, blend, 0.12F, body.yRot, 0.0F);
            }
            case STRETCH -> {
                float reach = swell;
                lerpArm(rightArm, blend * reach, -3.0F, 0.0F, -0.25F);
                lerpArm(leftArm, blend * reach, -3.0F, 0.0F, 0.25F);
                lerpRot(head, blend * reach, -0.4F, head.yRot, 0.0F);
            }
            case HAMMER -> {
                // A strike, not a sine: fast down, slow back up, which is what
                // makes it read as work rather than waving.
                float strike = phase < 0.3F
                        ? phase / 0.3F : 1.0F - (phase - 0.3F) / 0.7F;
                lerpArm(rightArm, blend, -2.4F + strike * 2.0F, 0.0F, -0.25F);
                lerpArm(leftArm, blend, -1.1F, 0.0F, 0.35F);
                lerpRot(body, blend, 0.18F, body.yRot, 0.0F);
                lerpRot(head, blend, 0.35F, head.yRot, 0.0F);
            }
            case SWEEP -> {
                lerpArm(rightArm, blend, -1.2F + wave * 0.25F, 0.0F, -0.4F + wave * 0.3F);
                lerpArm(leftArm, blend, -0.9F + wave * 0.2F, 0.0F, 0.3F);
                lerpRot(body, blend, 0.22F, body.yRot + wave * 0.14F, 0.0F);
                lerpRot(head, blend, 0.4F, head.yRot, 0.0F);
            }
            case SOW -> {
                float scatter = phase < 0.5F ? phase * 2.0F : 2.0F - phase * 2.0F;
                lerpArm(rightArm, blend, -0.6F - scatter * 0.9F, 0.0F, -0.3F - scatter * 0.5F);
                lerpArm(leftArm, blend, -1.3F, 0.0F, 0.6F);
                lerpRot(body, blend, 0.25F, body.yRot, 0.0F);
                lerpRot(head, blend, 0.45F, head.yRot, 0.0F);
            }
            case HARVEST -> {
                lerpArm(rightArm, blend, -0.9F + wave * 0.6F, 0.0F, -0.3F);
                lerpRot(body, blend, 0.55F, body.yRot + wave * 0.1F, 0.0F);
                lerpRot(head, blend, 0.5F, head.yRot, 0.0F);
            }
            case STIR -> {
                lerpArm(rightArm, blend, -1.5F, 0.0F, -0.3F + wave * 0.25F);
                lerpArm(leftArm, blend, -0.8F, 0.0F, 0.45F);
                lerpRot(body, blend, 0.2F, body.yRot, 0.0F);
                lerpRot(head, blend, 0.5F, head.yRot, 0.0F);
            }
            case WRITE -> {
                lerpArm(rightArm, blend, -1.55F, 0.0F, -0.25F + wave * 0.12F);
                lerpArm(leftArm, blend, -1.35F, 0.0F, 0.3F);
                lerpRot(body, blend, 0.28F, body.yRot, 0.0F);
                lerpRot(head, blend, 0.6F, head.yRot, 0.0F);
            }
            case WEIGH_COIN -> {
                lerpArm(rightArm, blend, -1.7F, 0.0F, -0.35F + wave * 0.14F);
                lerpArm(leftArm, blend, -1.5F, 0.0F, 0.3F);
                lerpRot(head, blend, 0.4F, head.yRot, 0.0F);
            }
            case HAGGLE -> {
                lerpArm(rightArm, blend, -1.3F + wave * 0.3F, 0.0F, -0.6F);
                lerpArm(leftArm, blend, -1.3F - wave * 0.3F, 0.0F, 0.6F);
                lerpRot(head, blend, -0.1F, head.yRot + wave * 0.1F, 0.0F);
            }
            case LUTE -> {
                lerpArm(rightArm, blend, -1.1F, 0.0F, -0.75F + wave * 0.3F);
                lerpArm(leftArm, blend, -1.6F, 0.0F, 0.8F);
                lerpRot(body, blend, 0.0F, body.yRot + wave * 0.06F, -0.08F);
                lerpRot(head, blend, -0.12F, head.yRot, 0.0F);
            }
            case PRAY -> {
                lerpArm(rightArm, blend, -1.9F, 0.0F, -0.3F);
                lerpArm(leftArm, blend, -1.9F, 0.0F, 0.3F);
                lerpRot(head, blend, 0.35F + wave * 0.04F, head.yRot, 0.0F);
                lerpRot(body, blend, 0.1F, body.yRot, 0.0F);
            }
            case CAST -> {
                float charge = swell;
                lerpArm(rightArm, blend, -2.6F * charge - 0.3F, 0.0F, -0.5F * charge);
                lerpArm(leftArm, blend, -1.2F * charge, 0.0F, 0.4F * charge);
                lerpRot(head, blend, -0.25F * charge, head.yRot, 0.0F);
            }
            case DRINK -> {
                float tip = swell;
                lerpArm(rightArm, blend, -1.4F - tip * 1.2F, 0.0F, -0.35F);
                lerpRot(head, blend, -0.45F * tip, head.yRot, 0.0F);
            }
            case SHARPEN -> {
                lerpArm(rightArm, blend, -1.25F, 0.0F, -0.4F + wave * 0.28F);
                lerpArm(leftArm, blend, -1.35F, 0.0F, 0.35F);
                lerpRot(body, blend, 0.2F, body.yRot, 0.0F);
                lerpRot(head, blend, 0.45F, head.yRot, 0.0F);
            }
            case HAUL -> {
                lerpArm(rightArm, blend, -2.3F, 0.0F, -0.55F);
                lerpArm(leftArm, blend, -2.3F, 0.0F, 0.55F);
                lerpRot(body, blend, 0.18F, body.yRot, 0.0F);
                lerpRot(head, blend, 0.1F, head.yRot, 0.0F);
            }
            case GUARD_STANCE -> {
                lerpArm(rightArm, blend, -0.55F, 0.0F, -0.25F);
                lerpArm(leftArm, blend, -0.9F, 0.0F, 0.55F);
                lerpRot(body, blend, 0.0F, body.yRot + 0.12F, 0.0F);
            }
            case TAUNT -> {
                lerpArm(rightArm, blend, -1.9F + wave * 0.5F, 0.0F, -0.5F);
                lerpRot(head, blend, -0.2F, head.yRot, 0.0F);
                lerpRot(body, blend, -0.1F, body.yRot, 0.0F);
            }
            case ROAR -> {
                float roar = swell;
                lerpArm(rightArm, blend, -0.8F - roar * 0.8F, 0.0F, -1.0F - roar * 0.3F);
                lerpArm(leftArm, blend, -0.8F - roar * 0.8F, 0.0F, 1.0F + roar * 0.3F);
                lerpRot(head, blend, -0.5F * roar, head.yRot, 0.0F);
                lerpRot(body, blend, -0.2F * roar, body.yRot, 0.0F);
            }
            case SCAN_HORIZON -> {
                lerpArm(rightArm, blend, -1.9F, 0.0F, -0.5F);
                lerpRot(head, blend, -0.1F, head.yRot + wave * 0.7F, 0.0F);
            }
            case DRAW_WEAPON -> {
                float draw = swell;
                lerpArm(rightArm, blend, -0.4F - draw * 1.6F, 0.0F, -0.2F - draw * 0.5F);
                lerpRot(body, blend, 0.0F, body.yRot + draw * 0.2F, 0.0F);
            }
            case SKULK -> {
                lerpRot(body, blend, 0.35F, body.yRot, 0.0F);
                lerpArm(rightArm, blend, -0.9F, 0.0F, -0.45F);
                lerpArm(leftArm, blend, -0.9F, 0.0F, 0.45F);
                lerpRot(head, blend, -0.25F, head.yRot + wave * 0.3F, 0.0F);
            }
            case PICKPOCKET -> {
                float reach = swell;
                lerpArm(rightArm, blend, -1.1F - reach * 0.7F, 0.0F, -0.15F);
                lerpRot(body, blend, 0.28F, body.yRot, 0.0F);
                lerpRot(head, blend, -0.2F, head.yRot + 0.5F, 0.0F);
            }
            case WHISPER -> {
                lerpArm(rightArm, blend, -2.2F, 0.0F, -0.7F);
                lerpRot(head, blend, 0.1F, head.yRot + 0.35F, 0.15F);
                lerpRot(body, blend, 0.12F, body.yRot, 0.0F);
            }
        }
    }

    private static void lerpArm(ModelPart arm, float blend, float xRot, float yRot, float zRot) {
        lerpRot(arm, blend, xRot, yRot, zRot);
    }

    /**
     * Blends a bone toward a target pose. Blending rather than assigning is what
     * lets the walk cycle and the head-tracking keep showing through the gesture.
     */
    private static void lerpRot(ModelPart part, float blend, float xRot, float yRot, float zRot) {
        part.xRot = Mth.lerp(blend, part.xRot, xRot);
        part.yRot = Mth.lerp(blend, part.yRot, yRot);
        part.zRot = Mth.lerp(blend, part.zRot, zRot);
    }
}
