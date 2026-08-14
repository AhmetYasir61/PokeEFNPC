package com.pokewing.pokeefnpc.menu;

import com.pokewing.pokeefnpc.npc.NpcEntity;
import com.pokewing.pokeefnpc.registry.ModMenus;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The container behind every NPC window.
 *
 * <p>There is exactly one of these for all thirty-odd roles. What makes a fence's
 * window different from a guild master's is not a different container — it is the
 * set of {@link MenuPage}s the role declares, which the screen assembles into a
 * tab strip. That keeps one menu type registered and one packet to maintain,
 * while a new role still gets an interface of its own for the cost of a line in
 * an enum.
 *
 * <p>The container itself carries only the player's own inventory. Buying,
 * selling, accepting contracts and paying dues all go through
 * {@code NpcActionPacket} instead of slot movement, because every one of those
 * has to be re-checked against the NPC's live stock, purse and opinion of the
 * player at the moment it happens — which is not something slot mechanics can
 * express.
 */
public class NpcMenu extends AbstractContainerMenu {

    @Nullable
    private final NpcEntity npc;
    private final Player player;

    /** Server-side constructor. */
    public NpcMenu(int containerId, Inventory inventory, @Nullable NpcEntity npc) {
        super(ModMenus.NPC_MENU.get(), containerId);
        this.npc = npc;
        this.player = inventory.player;
        addPlayerInventory(inventory);
    }

    /** Client-side constructor, from the entity id written at open time. */
    public NpcMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf.readVarInt()));
    }

    @Nullable
    private static NpcEntity resolve(Inventory inventory, int entityId) {
        var entity = inventory.player.level().getEntity(entityId);
        return entity instanceof NpcEntity npc ? npc : null;
    }

    @Nullable
    public NpcEntity npc() {
        return this.npc;
    }

    private void addPlayerInventory(Inventory inventory) {
        // Laid out to match the vanilla inventory grid so the screen can draw the
        // standard background under it without any of its own arithmetic.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Nothing to shift-click into: the container holds no slots of its own.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.npc == null) {
            return false;
        }
        // Walking away closes the window, and so does the shopkeeper dying or
        // being dragged off — the deal is only good while you are stood there.
        return this.npc.isAlive() && this.npc.distanceToSqr(player) < 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.npc != null && !this.npc.level().isClientSide) {
            this.npc.stopEmote();
        }
    }

    public Player player() {
        return this.player;
    }
}
