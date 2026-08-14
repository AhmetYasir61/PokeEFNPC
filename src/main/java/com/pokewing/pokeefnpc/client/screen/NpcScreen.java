package com.pokewing.pokeefnpc.client.screen;

import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.menu.MenuPage;
import com.pokewing.pokeefnpc.menu.NpcMenu;
import com.pokewing.pokeefnpc.net.NpcActionPacket;
import com.pokewing.pokeefnpc.net.PokeEFNPCNetwork;
import com.pokewing.pokeefnpc.quest.Quest;
import com.pokewing.pokeefnpc.shop.ShopEntry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * One screen, thirty-odd different windows.
 *
 * <p>The tab strip is built from the pages the role declared, so a fence's window
 * really is a different window from a guild master's — different tabs, different
 * title, different trim colour, different content — without a screen class per
 * trade. Adding a role gives it an interface for free.
 *
 * <p>Everything is drawn programmatically rather than from a background texture,
 * which is what lets the trim take the page's own accent colour and the mood
 * strip take the NPC's current emotion. Nothing here decides anything: every
 * click sends an {@code NpcActionPacket} and waits for the server to send the
 * window back, so what is on screen is always what the server believes.
 */
@OnlyIn(Dist.CLIENT)
public class NpcScreen extends AbstractContainerScreen<NpcMenu> {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 222;

    private static final int PANEL_BG = 0xFF1B1712;
    private static final int PANEL_EDGE = 0xFF3E3529;
    private static final int ROW_BG = 0xFF241F18;
    private static final int ROW_HOVER = 0xFF33291F;
    private static final int TEXT = 0xFFE8DCC4;
    private static final int TEXT_DIM = 0xFF9A8C74;

    private static final int CONTENT_TOP = 44;
    private static final int CONTENT_BOTTOM = 132;
    private static final int ROW_HEIGHT = 20;

    private MenuPage activePage = MenuPage.TALK;
    private int scroll;
    private int lastRevision = -1;

    /** Tab hit boxes, rebuilt whenever the page list changes. */
    private final List<MenuPage> tabs = new ArrayList<>();

    public NpcScreen(NpcMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        syncTabs();
    }

    private void syncTabs() {
        this.tabs.clear();
        // Ordinal order rather than declaration order, so TALK is always first
        // and a player's muscle memory survives moving between NPCs.
        for (MenuPage page : MenuPage.values()) {
            if (NpcMenuData.pages().contains(page)) {
                this.tabs.add(page);
            }
        }
        if (!this.tabs.contains(this.activePage)) {
            this.activePage = this.tabs.isEmpty() ? MenuPage.TALK : this.tabs.get(0);
        }
        this.lastRevision = NpcMenuData.revision();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (NpcMenuData.revision() != this.lastRevision) {
            syncTabs();
            this.scroll = 0;
        }
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        int accent = this.activePage.accentColor();
        graphics.fill(left, top, left + WIDTH, top + CONTENT_BOTTOM + 4, PANEL_BG);
        graphics.fill(left, top, left + WIDTH, top + 1, accent);
        graphics.fill(left, top + CONTENT_BOTTOM + 3, left + WIDTH, top + CONTENT_BOTTOM + 4,
                PANEL_EDGE);

        drawHeader(graphics, left, top);
        drawTabs(graphics, left, top, mouseX, mouseY);
        drawPage(graphics, left, top, mouseX, mouseY);
    }

    /** Name, trade, mood and standing — the same strip on every page. */
    private void drawHeader(GuiGraphics graphics, int left, int top) {
        graphics.drawString(this.font, this.title, left + 8, top + 6, TEXT, false);

        Component trade = Component.translatable(NpcMenuData.role().translationKey());
        graphics.drawString(this.font, trade.copy().withStyle(ChatFormatting.GRAY),
                left + 8, top + 17, TEXT_DIM, false);

        // The mood strip: colour for how the NPC feels, and the word for what it
        // thinks of you. Both are what the shop prices are actually made of, so
        // showing them is showing the player why they are being charged what they
        // are being charged.
        Component mood = Component.translatable(NpcMenuData.emotion().translationKey());
        int moodColor = moodColor();
        int moodWidth = this.font.width(mood) + 8;
        graphics.fill(left + WIDTH - moodWidth - 8, top + 4,
                left + WIDTH - 8, top + 15, moodColor);
        graphics.drawString(this.font, mood, left + WIDTH - moodWidth - 4, top + 6, 0xFF1B1712,
                false);

        Component standing = Component.translatable(NpcMenuData.standing().translationKey());
        int standingWidth = this.font.width(standing);
        graphics.drawString(this.font, standing, left + WIDTH - standingWidth - 8, top + 17,
                TEXT_DIM, false);
    }

    private int moodColor() {
        return switch (NpcMenuData.emotion()) {
            case ANGRY -> 0xFFD05A4A;
            case HURT -> 0xFFC47A5A;
            case SAD -> 0xFF6B84C4;
            case HAPPY -> 0xFF8AC46B;
            case SURPRISED -> 0xFFD4C46B;
            case FOCUSED -> 0xFF6BC4B8;
            case TIRED -> 0xFF8A8A8A;
            case NEUTRAL -> 0xFFB9A27A;
        };
    }

    private void drawTabs(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int x = left + 6;
        int y = top + 30;
        for (MenuPage page : this.tabs) {
            Component label = Component.translatable(page.translationKey());
            int width = this.font.width(label) + 10;
            boolean active = page == this.activePage;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12;

            graphics.fill(x, y, x + width, y + 12,
                    active ? page.accentColor() : hovered ? ROW_HOVER : ROW_BG);
            graphics.drawString(this.font, label, x + 5, y + 2,
                    active ? 0xFF1B1712 : TEXT, false);
            x += width + 2;

            // A role with many pages wraps rather than running off the panel.
            if (x > left + WIDTH - 40) {
                x = left + 6;
                y += 13;
            }
        }
    }

    private void drawPage(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        switch (this.activePage) {
            case SHOP, BLACK_MARKET -> drawShop(graphics, left, top, mouseX, mouseY);
            case QUESTS -> drawQuests(graphics, left, top, mouseX, mouseY);
            case GUILD -> drawGuild(graphics, left, top);
            case BANK -> drawBank(graphics, left, top, mouseX, mouseY);
            case SETTLEMENT -> drawSettlement(graphics, left, top);
            case SMITHING -> drawService(graphics, left, top, mouseX, mouseY,
                    "pokeefnpc.service.repair", NpcActionPacket.Action.REPAIR);
            case INFIRMARY -> drawService(graphics, left, top, mouseX, mouseY,
                    "pokeefnpc.service.heal", NpcActionPacket.Action.HEAL);
            case LODGING -> drawService(graphics, left, top, mouseX, mouseY,
                    "pokeefnpc.service.rest", NpcActionPacket.Action.REST);
            default -> drawTalk(graphics, left, top, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ pages

    private void drawTalk(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int y = top + CONTENT_TOP + 4;
        graphics.drawWordWrap(this.font,
                Component.translatable("pokeefnpc.talk." + NpcMenuData.role().key()),
                left + 10, y, WIDTH - 20, TEXT);

        // Asking for a gesture is the one thing on this page that does anything,
        // and it is gated server-side on the NPC actually liking you.
        int buttonY = top + CONTENT_BOTTOM - 24;
        Emote[] offered = {Emote.WAVE, Emote.BOW, Emote.CHEER, Emote.LAUGH};
        int x = left + 10;
        for (Emote emote : offered) {
            Component label = Component.translatable(emote.translationKey());
            int width = this.font.width(label) + 10;
            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= buttonY && mouseY < buttonY + 14;
            graphics.fill(x, buttonY, x + width, buttonY + 14, hovered ? ROW_HOVER : ROW_BG);
            graphics.drawString(this.font, label, x + 5, buttonY + 3, TEXT, false);
            x += width + 4;
        }
    }

    private void drawShop(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<ShopEntry> stock = NpcMenuData.shop();
        if (stock.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("pokeefnpc.shop.empty"),
                    left + 10, top + CONTENT_TOP + 6, TEXT_DIM, false);
            return;
        }
        int rows = (CONTENT_BOTTOM - CONTENT_TOP) / ROW_HEIGHT;
        for (int row = 0; row < rows; row++) {
            int index = row + this.scroll;
            if (index >= stock.size()) {
                break;
            }
            ShopEntry entry = stock.get(index);
            int y = top + CONTENT_TOP + row * ROW_HEIGHT;
            boolean hovered = mouseX >= left + 8 && mouseX < left + WIDTH - 8
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;

            graphics.fill(left + 8, y, left + WIDTH - 8, y + ROW_HEIGHT - 2,
                    hovered ? ROW_HOVER : ROW_BG);
            // Contraband gets a mark, because knowing which half of a fence's
            // slate is the dangerous half is the entire point of the page.
            if (entry.contraband()) {
                graphics.fill(left + 8, y, left + 10, y + ROW_HEIGHT - 2, 0xFFB04A4A);
            }

            ItemStack stack = entry.offer();
            graphics.renderItem(stack, left + 13, y + 1);
            graphics.drawString(this.font, stack.getHoverName(), left + 34, y + 2, TEXT, false);

            Component detail = entry.buying()
                    ? Component.translatable("pokeefnpc.shop.buying")
                    : Component.translatable("pokeefnpc.shop.stock",
                            entry.unlimited() ? "-" : String.valueOf(entry.stock()));
            graphics.drawString(this.font, detail, left + 34, y + 11, TEXT_DIM, false);

            Component price = Component.translatable("pokeefnpc.shop.price",
                    NpcMenuData.priceAt(index));
            graphics.drawString(this.font, price,
                    left + WIDTH - 14 - this.font.width(price), y + 6, 0xFFD4AF37, false);
        }
    }

    private void drawQuests(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<Quest> offered = NpcMenuData.quests();
        if (offered.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("pokeefnpc.quest.none"),
                    left + 10, top + CONTENT_TOP + 6, TEXT_DIM, false);
            return;
        }
        int rows = (CONTENT_BOTTOM - CONTENT_TOP) / (ROW_HEIGHT + 4);
        for (int row = 0; row < rows; row++) {
            int index = row + this.scroll;
            if (index >= offered.size()) {
                break;
            }
            Quest quest = offered.get(index);
            int progress = NpcMenuData.progressAt(index);
            int y = top + CONTENT_TOP + row * (ROW_HEIGHT + 4);
            boolean hovered = mouseX >= left + 8 && mouseX < left + WIDTH - 8
                    && mouseY >= y && mouseY < y + ROW_HEIGHT + 2;

            graphics.fill(left + 8, y, left + WIDTH - 8, y + ROW_HEIGHT + 2,
                    hovered ? ROW_HOVER : ROW_BG);
            graphics.drawString(this.font, quest.describe(), left + 12, y + 3, TEXT, false);

            Component status = progress < 0
                    ? Component.translatable("pokeefnpc.quest.available")
                    : Component.translatable("pokeefnpc.quest.progress", progress,
                            quest.required());
            graphics.drawString(this.font, status, left + 12, y + 13, TEXT_DIM, false);

            Component reward = Component.translatable("pokeefnpc.quest.reward",
                    quest.rewardEmeralds());
            graphics.drawString(this.font, reward,
                    left + WIDTH - 14 - this.font.width(reward), y + 8, 0xFFD4AF37, false);
        }
    }

    private void drawGuild(GuiGraphics graphics, int left, int top) {
        int y = top + CONTENT_TOP + 6;
        var rank = NpcMenuData.rank();

        graphics.drawString(this.font, Component.translatable("pokeefnpc.guild.rank",
                Component.translatable(rank.translationKey())), left + 10, y, rank.color(), false);
        y += 14;
        graphics.drawString(this.font, Component.translatable("pokeefnpc.guild.points",
                NpcMenuData.guildPoints()), left + 10, y, TEXT, false);
        y += 12;

        int toNext = com.pokewing.pokeefnpc.guild.GuildRank.pointsToNext(NpcMenuData.guildPoints());
        graphics.drawString(this.font, toNext > 0
                        ? Component.translatable("pokeefnpc.guild.to_next", toNext)
                        : Component.translatable("pokeefnpc.guild.top_rank"),
                left + 10, y, TEXT_DIM, false);
        y += 16;

        // The ladder, so the reward for climbing is legible before you climb it.
        int barLeft = left + 10;
        int barWidth = WIDTH - 20;
        graphics.fill(barLeft, y, barLeft + barWidth, y + 6, ROW_BG);
        var ranks = com.pokewing.pokeefnpc.guild.GuildRank.values();
        int filled = Math.round(barWidth * (rank.level() / (float) (ranks.length - 1)));
        graphics.fill(barLeft, y, barLeft + filled, y + 6, rank.color());
        y += 14;

        graphics.drawString(this.font,
                Component.translatable("pokeefnpc.guild.discount",
                        Math.round((1.0F - rank.discount()) * 100.0F)),
                left + 10, y, TEXT_DIM, false);
    }

    private void drawBank(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int y = top + CONTENT_TOP + 6;
        graphics.drawString(this.font,
                Component.translatable("pokeefnpc.bank.balance", NpcMenuData.bankBalance()),
                left + 10, y, 0xFFD4AF37, false);
        y += 20;

        int[] amounts = {1, 16, 64};
        drawAmountRow(graphics, left, y, mouseX, mouseY, "pokeefnpc.bank.deposit", amounts);
        drawAmountRow(graphics, left, y + 20, mouseX, mouseY, "pokeefnpc.bank.withdraw", amounts);
    }

    private void drawAmountRow(GuiGraphics graphics, int left, int y, int mouseX, int mouseY,
                               String labelKey, int[] amounts) {
        graphics.drawString(this.font, Component.translatable(labelKey), left + 10, y + 3,
                TEXT, false);
        int x = left + 90;
        for (int amount : amounts) {
            String label = String.valueOf(amount);
            int width = this.font.width(label) + 12;
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
            graphics.fill(x, y, x + width, y + 14, hovered ? ROW_HOVER : ROW_BG);
            graphics.drawString(this.font, label, x + 6, y + 3, TEXT, false);
            x += width + 4;
        }
    }

    private void drawService(GuiGraphics graphics, int left, int top, int mouseX, int mouseY,
                             String labelKey, NpcActionPacket.Action action) {
        int y = top + CONTENT_TOP + 6;
        graphics.drawWordWrap(this.font, Component.translatable(labelKey + ".description"),
                left + 10, y, WIDTH - 20, TEXT_DIM);

        int buttonY = top + CONTENT_TOP + 40;
        Component label = Component.translatable(labelKey);
        int width = this.font.width(label) + 16;
        boolean hovered = mouseX >= left + 10 && mouseX < left + 10 + width
                && mouseY >= buttonY && mouseY < buttonY + 16;
        graphics.fill(left + 10, buttonY, left + 10 + width, buttonY + 16,
                hovered ? ROW_HOVER : ROW_BG);
        graphics.drawString(this.font, label, left + 18, buttonY + 4, TEXT, false);
    }

    private void drawSettlement(GuiGraphics graphics, int left, int top) {
        int y = top + CONTENT_TOP + 4;
        String name = NpcMenuData.settlementName();
        if (name.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("pokeefnpc.settlement.none"),
                    left + 10, y, TEXT_DIM, false);
            return;
        }
        graphics.drawString(this.font, Component.literal(name)
                .withStyle(ChatFormatting.GOLD), left + 10, y, TEXT, false);
        y += 14;
        graphics.drawString(this.font, Component.translatable("pokeefnpc.settlement.population",
                NpcMenuData.population()), left + 10, y, TEXT, false);
        y += 12;
        graphics.drawString(this.font, Component.translatable("pokeefnpc.settlement.stores",
                NpcMenuData.foodStores(), NpcMenuData.materialStores()), left + 10, y, TEXT, false);
        y += 16;

        drawMeter(graphics, left + 10, y, "pokeefnpc.settlement.defence",
                NpcMenuData.defence(), 0xFF6BA88E);
        y += 16;
        drawMeter(graphics, left + 10, y, "pokeefnpc.settlement.threat",
                NpcMenuData.threat(), 0xFFD05A4A);
        y += 16;
        drawMeter(graphics, left + 10, y, "pokeefnpc.settlement.prosperity",
                NpcMenuData.prosperity(), 0xFFD4AF37);
    }

    private void drawMeter(GuiGraphics graphics, int x, int y, String labelKey, float value,
                           int color) {
        graphics.drawString(this.font, Component.translatable(labelKey), x, y, TEXT_DIM, false);
        int barLeft = x + 80;
        int barWidth = WIDTH - 100;
        graphics.fill(barLeft, y, barLeft + barWidth, y + 7, ROW_BG);
        graphics.fill(barLeft, y, barLeft + Math.round(barWidth * Math.max(0.0F, Math.min(1.0F,
                value))), y + 7, color);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleTabClick(mouseX, mouseY) || handleContentClick(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleTabClick(double mouseX, double mouseY) {
        int x = this.leftPos + 6;
        int y = this.topPos + 30;
        for (MenuPage page : this.tabs) {
            int width = this.font.width(Component.translatable(page.translationKey())) + 10;
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12) {
                this.activePage = page;
                this.scroll = 0;
                return true;
            }
            x += width + 2;
            if (x > this.leftPos + WIDTH - 40) {
                x = this.leftPos + 6;
                y += 13;
            }
        }
        return false;
    }

    private boolean handleContentClick(double mouseX, double mouseY, int button) {
        return switch (this.activePage) {
            case SHOP, BLACK_MARKET -> clickShop(mouseX, mouseY, button);
            case QUESTS -> clickQuest(mouseX, mouseY, button);
            case BANK -> clickBank(mouseX, mouseY);
            case SMITHING -> clickService(mouseX, mouseY, NpcActionPacket.Action.REPAIR);
            case INFIRMARY -> clickService(mouseX, mouseY, NpcActionPacket.Action.HEAL);
            case LODGING -> clickService(mouseX, mouseY, NpcActionPacket.Action.REST);
            case TALK -> clickEmote(mouseX, mouseY);
            default -> false;
        };
    }

    private boolean clickShop(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseY, ROW_HEIGHT);
        List<ShopEntry> stock = NpcMenuData.shop();
        if (index < 0 || index >= stock.size() || !inContent(mouseX)) {
            return false;
        }
        ShopEntry entry = stock.get(index);
        // Right-click and shift both mean "a stack of them", which is the
        // convention players already have from vanilla trading.
        int amount = button == 1 || hasShiftDown() ? 16 : 1;
        send(NpcActionPacket.shop(NpcMenuData.entityId(),
                entry.buying() ? NpcActionPacket.Action.SELL : NpcActionPacket.Action.BUY,
                index, amount));
        return true;
    }

    private boolean clickQuest(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseY, ROW_HEIGHT + 4);
        List<Quest> offered = NpcMenuData.quests();
        if (index < 0 || index >= offered.size() || !inContent(mouseX)) {
            return false;
        }
        Quest quest = offered.get(index);
        int progress = NpcMenuData.progressAt(index);

        NpcActionPacket.Action action;
        if (progress < 0) {
            action = NpcActionPacket.Action.ACCEPT_QUEST;
        } else if (button == 1) {
            action = NpcActionPacket.Action.ABANDON_QUEST;
        } else {
            action = NpcActionPacket.Action.TURN_IN_QUEST;
        }
        send(NpcActionPacket.quest(NpcMenuData.entityId(), action, quest.id()));
        return true;
    }

    private boolean clickBank(double mouseX, double mouseY) {
        int depositY = this.topPos + CONTENT_TOP + 26;
        int withdrawY = depositY + 20;
        int[] amounts = {1, 16, 64};

        for (int pass = 0; pass < 2; pass++) {
            int y = pass == 0 ? depositY : withdrawY;
            if (mouseY < y || mouseY >= y + 14) {
                continue;
            }
            int x = this.leftPos + 90;
            for (int amount : amounts) {
                int width = this.font.width(String.valueOf(amount)) + 12;
                if (mouseX >= x && mouseX < x + width) {
                    send(NpcActionPacket.simple(NpcMenuData.entityId(),
                            pass == 0 ? NpcActionPacket.Action.DEPOSIT
                                    : NpcActionPacket.Action.WITHDRAW, amount));
                    return true;
                }
                x += width + 4;
            }
        }
        return false;
    }

    private boolean clickService(double mouseX, double mouseY, NpcActionPacket.Action action) {
        int buttonY = this.topPos + CONTENT_TOP + 40;
        if (mouseY < buttonY || mouseY >= buttonY + 16 || mouseX < this.leftPos + 10) {
            return false;
        }
        send(NpcActionPacket.simple(NpcMenuData.entityId(), action, 1));
        return true;
    }

    private boolean clickEmote(double mouseX, double mouseY) {
        int buttonY = this.topPos + CONTENT_BOTTOM - 24;
        if (mouseY < buttonY || mouseY >= buttonY + 14) {
            return false;
        }
        Emote[] offered = {Emote.WAVE, Emote.BOW, Emote.CHEER, Emote.LAUGH};
        int x = this.leftPos + 10;
        for (Emote emote : offered) {
            int width = this.font.width(Component.translatable(emote.translationKey())) + 10;
            if (mouseX >= x && mouseX < x + width) {
                send(NpcActionPacket.shop(NpcMenuData.entityId(),
                        NpcActionPacket.Action.ASK_EMOTE, emote.ordinal(), 1));
                return true;
            }
            x += width + 4;
        }
        return false;
    }

    private int rowAt(double mouseY, int rowHeight) {
        int relative = (int) (mouseY - (this.topPos + CONTENT_TOP));
        if (relative < 0 || relative > CONTENT_BOTTOM - CONTENT_TOP) {
            return -1;
        }
        return relative / rowHeight + this.scroll;
    }

    private boolean inContent(double mouseX) {
        return mouseX >= this.leftPos + 8 && mouseX < this.leftPos + WIDTH - 8;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int size = this.activePage == MenuPage.QUESTS
                ? NpcMenuData.quests().size() : NpcMenuData.shop().size();
        int visible = (CONTENT_BOTTOM - CONTENT_TOP) / ROW_HEIGHT;
        if (size > visible) {
            this.scroll = Math.max(0, Math.min(size - visible, this.scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private static void send(NpcActionPacket packet) {
        PokeEFNPCNetwork.sendToServer(packet);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // The header already carries the title; drawing the vanilla labels on top
        // of a custom panel just produces two overlapping names.
    }
}
