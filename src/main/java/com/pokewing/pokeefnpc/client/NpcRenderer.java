package com.pokewing.pokeefnpc.client;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Draws an NPC.
 *
 * <p>Built on {@link HumanoidMobRenderer} so armour and held items work without
 * any special handling, with two additions: the skin comes from the role, and
 * {@link NpcFaceLayer} is added last so the face is drawn over everything else on
 * the head.
 */
@OnlyIn(Dist.CLIENT)
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, NpcModel> {

    public static final ModelLayerLocation MAIN_LAYER = new ModelLayerLocation(
            new ResourceLocation(PokeEFNPC.MOD_ID, "npc"), "main");

    /** Used when a role has no skin of its own shipped with the mod. */
    private static final ResourceLocation FALLBACK =
            new ResourceLocation("textures/entity/player/wide/steve.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel(context.bakeLayer(MAIN_LAYER), false), 0.5F);

        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new NpcFaceLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity npc) {
        // Role skins are optional resources: an install that has not drawn one
        // for, say, the executioner falls back to the default player skin rather
        // than rendering a missing-texture NPC.
        ResourceLocation roleTexture = npc.role().texture();
        return NpcSkins.exists(roleTexture) ? roleTexture : FALLBACK;
    }
}
