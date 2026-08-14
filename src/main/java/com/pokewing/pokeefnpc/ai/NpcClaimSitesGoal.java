package com.pokewing.pokeefnpc.ai;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.schedule.SiteResolver;
import com.pokewing.pokeefnpc.settlement.Settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Moving in.
 *
 * <p>An NPC without a bed and a bench has no day — the schedule resolves every
 * site to the village square and the whole population mills around in one place.
 * This goal is what turns a spawned character into a resident: it finds an
 * unclaimed bed, then an unclaimed workstation of the kind its trade uses, and
 * registers both with the {@link Settlement} so nobody else takes them.
 *
 * <p>It runs rarely and stops as soon as both are held, so the scan cost is paid
 * once per character rather than continuously.
 */
public class NpcClaimSitesGoal extends Goal {

    private static final int RETRY_INTERVAL = 200;

    private final NpcEntity npc;
    private int cooldown;

    public NpcClaimSitesGoal(NpcEntity npc) {
        this.npc = npc;
        setFlags(EnumSet.noneOf(Flag.class));
        this.cooldown = npc.getRandom().nextInt(RETRY_INTERVAL);
    }

    @Override
    public boolean canUse() {
        if (this.npc.homePos() != null && this.npc.workPos() != null) {
            return false;
        }
        return --this.cooldown <= 0;
    }

    @Override
    public void start() {
        this.cooldown = RETRY_INTERVAL;

        Settlement settlement = this.npc.settlement();
        if (settlement == null) {
            // Standing in a place that has become a village since this NPC
            // arrived: try again to belong somewhere before claiming anything.
            this.npc.joinNearestSettlement();
            settlement = this.npc.settlement();
        }
        long gameTime = this.npc.level().getGameTime();

        if (this.npc.homePos() == null) {
            BlockPos bed = SiteResolver.findNearby(this.npc, SiteResolver.Kind.BED);
            if (bed != null && (settlement == null
                    || settlement.claimHome(this.npc.getUUID(), bed, gameTime))) {
                this.npc.setHomePos(bed);
                // Having somewhere of your own is worth something.
                this.npc.mood().feel(this.npc.personality(), 0.3F, 0.1F);
                this.npc.playEmote(Emote.NOD);
            }
        }

        if (this.npc.workPos() == null) {
            SiteResolver.Kind kind = SiteResolver.workstationKind(this.npc.role());
            BlockPos bench = SiteResolver.findNearby(this.npc, kind);
            if (bench != null && (settlement == null
                    || settlement.claimWorkstation(this.npc.getUUID(), bench, gameTime))) {
                this.npc.setWorkPos(bench);
                this.npc.mood().feel(this.npc.personality(), 0.2F, 0.1F);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        // A one-shot: everything happens in start().
        return false;
    }
}
