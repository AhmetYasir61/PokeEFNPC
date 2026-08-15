package com.pokewing.pokeefnpc.client.screen;

import com.pokewing.pokeefnpc.npc.Allegiance;
import com.pokewing.pokeefnpc.net.CommandPacket;
import com.pokewing.pokeefnpc.net.PokeEFNPCNetwork;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The field command screen: orders given without stopping.
 *
 * <p>Opened on a key rather than by walking up to somebody, because the moment
 * you most need to tell your soldiers what to do is the moment you cannot afford
 * to stand still. So this screen deliberately breaks the usual rule that a menu
 * takes the keyboard: while it is open, the movement keys still drive the
 * player. It reads the physical key state each frame and pushes it back into
 * vanilla's own key mappings, which means you walk, sprint, jump and turn with
 * the panel up.
 *
 * <p>Typing into the order box suspends that, for the obvious reason — a "w" is
 * a letter while you are writing a sentence, not a step forward.
 *
 * <p>Everything it shows comes from the server on open, and every button sends
 * an intent rather than a result. The screen cannot make a soldier do anything;
 * it can only ask, and the server decides whether that soldier is yours.
 */
@OnlyIn(Dist.CLIENT)
public class CommandScreen extends Screen {

    /** The roster as the server last described it. Static so the packet can fill it. */
    private static List<CommandPacket.Unit> roster = List.of();

    private static final int ROW_HEIGHT = 22;
    private static final int PANEL_WIDTH = 260;

    private EditBox orderBox;
    private int selected = -1;
    private int refreshTimer;
    /** Movement keys held down by this screen, so they can be released on close. */
    private final List<KeyMapping> heldByUs = new ArrayList<>();

    public CommandScreen() {
        super(Component.translatable("pokeefnpc.command.title"));
    }

    /** Called from the packet handler when a fresh roster arrives. */
    public static void acceptRoster(List<CommandPacket.Unit> units) {
        roster = units;
    }

    /**
     * A screen that does not pause the game. On a server this changes nothing;
     * in single player it is the difference between giving an order mid-fight
     * and freezing the fight to give it.
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        PokeEFNPCNetwork.sendToServer(CommandPacket.request());

        int left = 8;
        int bottom = this.height - 30;

        // Orders to everybody at once, along the bottom.
        int x = left;
        for (Allegiance.Stance stance : Allegiance.Stance.values()) {
            addRenderableWidget(Button.builder(
                            Component.translatable("pokeefnpc.command.order." + stance.name().toLowerCase(java.util.Locale.ROOT)),
                            button -> order(stance))
                    .bounds(x, bottom, 62, 20).build());
            x += 64;
        }

        this.orderBox = new EditBox(this.font, left, bottom - 24, PANEL_WIDTH - 70, 18,
                Component.translatable("pokeefnpc.command.order_box"));
        this.orderBox.setMaxLength(200);
        this.orderBox.setHint(Component.translatable("pokeefnpc.command.order_hint"));
        addRenderableWidget(this.orderBox);

        addRenderableWidget(Button.builder(Component.translatable("pokeefnpc.command.send"),
                        button -> sendSpoken())
                .bounds(left + PANEL_WIDTH - 66, bottom - 25, 62, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        // The roster goes stale as soldiers walk, fight and die, so it is asked
        // for again while the panel is up rather than frozen at open.
        if (--this.refreshTimer <= 0) {
            this.refreshTimer = 20;
            PokeEFNPCNetwork.sendToServer(CommandPacket.request());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        keepPlayerMoving();
        renderBackground(graphics);

        graphics.drawString(this.font, this.title, 8, 8, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("pokeefnpc.command.count", roster.size()),
                8, 20, 0xA0A0A0);

        int y = 34;
        for (int index = 0; index < roster.size() && y < this.height - 60; index++) {
            CommandPacket.Unit unit = roster.get(index);
            boolean chosen = index == this.selected;
            int background = chosen ? 0x66FFFFFF : 0x33000000;
            graphics.fill(6, y - 2, 6 + PANEL_WIDTH, y + ROW_HEIGHT - 6, background);

            graphics.drawString(this.font, unit.name(), 10, y, 0xFFFFFF);
            graphics.drawString(this.font,
                    Component.translatable(stanceKey(unit.stance())), 10, y + 10, 0xC8C8A0);

            String distance = unit.distance() < 0
                    ? "—" : unit.distance() + "m";
            graphics.drawString(this.font, distance, 6 + PANEL_WIDTH - 34, y, 0x9AB4C8);

            // Health as a bar rather than a number: at a glance, mid-fight, is
            // anybody about to die.
            int barWidth = 40;
            int filled = unit.maxHealth() <= 0 ? 0
                    : Math.round(barWidth * Math.min(1.0F, unit.health() / unit.maxHealth()));
            graphics.fill(6 + PANEL_WIDTH - 46, y + 11, 6 + PANEL_WIDTH - 46 + barWidth, y + 16,
                    0xFF3A3A3A);
            graphics.fill(6 + PANEL_WIDTH - 46, y + 11, 6 + PANEL_WIDTH - 46 + filled, y + 16,
                    filled > barWidth / 3 ? 0xFF4CAF50 : 0xFFCC3333);
            y += ROW_HEIGHT;
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String stanceKey(int ordinal) {
        Allegiance.Stance[] values = Allegiance.Stance.values();
        return "pokeefnpc.command.order."
                + values[Math.floorMod(ordinal, values.length)].name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= 6 && mouseX <= 6 + PANEL_WIDTH && mouseY >= 32) {
            int index = (int) ((mouseY - 34) / ROW_HEIGHT);
            if (index >= 0 && index < roster.size()) {
                // Clicking the one already selected clears it, which is how you
                // go back to ordering the whole company.
                this.selected = this.selected == index ? -1 : index;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (this.orderBox.isFocused() && key == InputConstants.KEY_RETURN) {
            sendSpoken();
            return true;
        }
        // Number keys pick a unit, so a company can be commanded without the
        // mouse leaving whatever it was doing.
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9
                && !this.orderBox.isFocused()) {
            int index = key - InputConstants.KEY_1;
            this.selected = index < roster.size() ? index : -1;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void order(Allegiance.Stance stance) {
        PokeEFNPCNetwork.sendToServer(CommandPacket.order(targetId(), stance));
    }

    private void sendSpoken() {
        String line = this.orderBox.getValue().trim();
        if (line.isEmpty()) {
            return;
        }
        PokeEFNPCNetwork.sendToServer(CommandPacket.speak(targetId(), line));
        this.orderBox.setValue("");
    }

    /** The selected unit, or -1 meaning everybody. */
    private int targetId() {
        return this.selected >= 0 && this.selected < roster.size()
                ? roster.get(this.selected).entityId() : -1;
    }

    /**
     * Pushes the physically-held movement keys back into vanilla's key mappings.
     *
     * <p>A {@link Screen} normally swallows input, which is why menus stop you
     * walking. Reading the real key state straight from the window and setting
     * the mapping's own down flag puts the movement back — the player keeps
     * running, jumping and sprinting exactly as though nothing were open.
     *
     * <p>Suspended while the order box has focus: while you are typing a
     * sentence, "w" is a letter.
     */
    private void keepPlayerMoving() {
        releaseHeldKeys();
        if (this.minecraft == null || this.orderBox == null || this.orderBox.isFocused()) {
            return;
        }
        var options = this.minecraft.options;
        for (KeyMapping mapping : new KeyMapping[] {
                options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
                options.keyJump, options.keySprint, options.keyShift }) {
            if (isPhysicallyDown(mapping)) {
                mapping.setDown(true);
                this.heldByUs.add(mapping);
            }
        }
    }

    private boolean isPhysicallyDown(KeyMapping mapping) {
        var key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        return InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    /** Lets go of anything this screen pressed, so nothing stays stuck down. */
    private void releaseHeldKeys() {
        for (KeyMapping mapping : this.heldByUs) {
            mapping.setDown(false);
        }
        this.heldByUs.clear();
    }

    @Override
    public void onClose() {
        releaseHeldKeys();
        super.onClose();
    }
}
