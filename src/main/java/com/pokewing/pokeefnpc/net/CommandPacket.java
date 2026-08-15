package com.pokewing.pokeefnpc.net;

import com.pokewing.pokeefnpc.npc.Allegiance;
import com.pokewing.pokeefnpc.npc.NpcEntity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The command screen's whole conversation with the server, in one packet type.
 *
 * <p>Three things travel over it: a request for the roster, the roster itself,
 * and an order. Keeping them together means one registration and one place to
 * check that the player is actually entitled to what they are asking for — and
 * that check is the reason none of this trusts the client. The screen may name
 * any entity id it likes; the server only ever acts on NPCs that are genuinely
 * sworn to the player who sent the packet.
 */
public final class CommandPacket {

    public enum Kind {
        /** Client → server: send me my roster. */
        REQUEST,
        /** Server → client: here it is. */
        ROSTER,
        /** Client → server: this unit takes this stance. */
        ORDER,
        /** Client → server: this unit hears this line, in words. */
        SPEAK
    }

    /** One soldier as the command screen needs to show it. */
    public record Unit(int entityId, String name, String role, int stance,
                       float health, float maxHealth, int distance) {
    }

    private final Kind kind;
    private final int entityId;
    private final int stance;
    private final String text;
    private final List<Unit> units;

    private CommandPacket(Kind kind, int entityId, int stance, String text, List<Unit> units) {
        this.kind = kind;
        this.entityId = entityId;
        this.stance = stance;
        this.text = text;
        this.units = units;
    }

    public static CommandPacket request() {
        return new CommandPacket(Kind.REQUEST, -1, 0, "", List.of());
    }

    public static CommandPacket roster(List<Unit> units) {
        return new CommandPacket(Kind.ROSTER, -1, 0, "", units);
    }

    /** An order for one unit, or for every unit when {@code entityId} is -1. */
    public static CommandPacket order(int entityId, Allegiance.Stance stance) {
        return new CommandPacket(Kind.ORDER, entityId, stance.ordinal(), "", List.of());
    }

    public static CommandPacket speak(int entityId, String text) {
        return new CommandPacket(Kind.SPEAK, entityId, 0, text, List.of());
    }

    public List<Unit> units() {
        return this.units;
    }

    public Kind kind() {
        return this.kind;
    }

    public static void encode(CommandPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.kind);
        buffer.writeVarInt(packet.entityId);
        buffer.writeVarInt(packet.stance);
        buffer.writeUtf(packet.text, 256);
        buffer.writeVarInt(packet.units.size());
        for (Unit unit : packet.units) {
            buffer.writeVarInt(unit.entityId());
            buffer.writeUtf(unit.name(), 64);
            buffer.writeUtf(unit.role(), 64);
            buffer.writeVarInt(unit.stance());
            buffer.writeFloat(unit.health());
            buffer.writeFloat(unit.maxHealth());
            buffer.writeVarInt(unit.distance());
        }
    }

    public static CommandPacket decode(FriendlyByteBuf buffer) {
        Kind kind = buffer.readEnum(Kind.class);
        int entityId = buffer.readVarInt();
        int stance = buffer.readVarInt();
        String text = buffer.readUtf(256);
        int count = buffer.readVarInt();
        List<Unit> units = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            units.add(new Unit(buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(64),
                    buffer.readVarInt(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readVarInt()));
        }
        return new CommandPacket(kind, entityId, stance, text, units);
    }

    public static void handle(CommandPacket packet, Supplier<net.minecraftforge.network.NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) {
                // Server → client: the screen picks it up itself, on its own side.
                ClientCommandHooks.acceptRoster(packet);
                return;
            }
            switch (packet.kind) {
                case REQUEST -> PokeEFNPCNetwork.sendTo(player, roster(gather(player)));
                case ORDER -> applyOrder(player, packet);
                case SPEAK -> speakTo(player, packet);
                default -> {
                }
            }
        });
        ctx.setPacketHandled(true);
    }

    /** Everyone in the world sworn to this player, nearest first. */
    private static List<Unit> gather(ServerPlayer player) {
        List<Unit> units = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            for (NpcEntity npc : level.getEntities(EntityTypeTest.forClass(NpcEntity.class),
                    candidate -> candidate.allegiance().isOwnedBy(player.getUUID()))) {
                units.add(new Unit(npc.getId(),
                        npc.getName().getString(),
                        npc.role().translationKey(),
                        npc.allegiance().stance().ordinal(),
                        npc.getHealth(), npc.getMaxHealth(),
                        npc.level() == player.level()
                                ? (int) Math.sqrt(npc.distanceToSqr(player)) : -1));
            }
        }
        units.sort(java.util.Comparator.comparingInt(unit ->
                unit.distance() < 0 ? Integer.MAX_VALUE : unit.distance()));
        return units;
    }

    private static void applyOrder(ServerPlayer player, CommandPacket packet) {
        Allegiance.Stance stance = Allegiance.Stance.values()[
                Math.floorMod(packet.stance, Allegiance.Stance.values().length)];
        for (NpcEntity npc : owned(player, packet.entityId)) {
            npc.setStance(stance);
            npc.clearRallyPoint();
        }
    }

    private static void speakTo(ServerPlayer player, CommandPacket packet) {
        String line = packet.text.trim();
        if (line.isEmpty()) {
            return;
        }
        for (NpcEntity npc : owned(player, packet.entityId)) {
            // Straight into the same path a typed or spoken line takes, so an
            // order given through the screen is understood exactly as well as
            // one shouted at the NPC's face.
            npc.heard(player, line, false);
        }
    }

    /**
     * The NPCs this player may actually command: one by id, or all of them when
     * the id is -1. An id that is not theirs resolves to nothing.
     */
    private static List<NpcEntity> owned(ServerPlayer player, int entityId) {
        List<NpcEntity> found = new ArrayList<>();
        for (ServerLevel level : player.server.getAllLevels()) {
            if (entityId >= 0) {
                if (level.getEntity(entityId) instanceof NpcEntity npc
                        && npc.allegiance().isOwnedBy(player.getUUID())) {
                    found.add(npc);
                }
            } else {
                for (NpcEntity npc : level.getEntities(EntityTypeTest.forClass(NpcEntity.class),
                        candidate -> candidate.allegiance().isOwnedBy(player.getUUID()))) {
                    found.add(npc);
                }
            }
        }
        return found;
    }
}
