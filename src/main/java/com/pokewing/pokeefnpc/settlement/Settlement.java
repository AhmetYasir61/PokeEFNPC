package com.pokewing.pokeefnpc.settlement;

import com.pokewing.pokeefnpc.npc.NpcRole;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One inhabited place, and everything it collectively knows.
 *
 * <p>An NPC on its own can walk a routine; it takes a settlement for that
 * routine to add up to a society. This is where the shared facts live: who lives
 * here, which bed and which workstation each of them has taken, where the walls
 * are, how much food is in the stores, and how frightened everyone currently is.
 *
 * <p>The important consequence is that the individual AI stays simple. A guard
 * does not need to survey the perimeter to decide to build a palisade — it asks
 * the settlement where the {@link #weakPoints} are. A farmer does not decide on
 * its own to hide; the settlement's {@link #threat} says the place is under
 * attack and every role reads that one number through its own courage.
 */
public final class Settlement {

    /** Claimed slots are held for this long after the holder was last seen. */
    private static final int CLAIM_EXPIRY_TICKS = 24000 * 3;

    private final UUID id;
    private String name;
    private BlockPos center;
    private int radius;

    /** Everyone who lives here, whether currently loaded or not. */
    private final Set<UUID> residents = new HashSet<>();
    /** Bed or home marker each resident has claimed. */
    private final Map<UUID, BlockPos> homes = new HashMap<>();
    /** Workstation each resident has claimed, so two smiths do not share an anvil. */
    private final Map<UUID, BlockPos> workstations = new HashMap<>();
    /** When each claim was last confirmed, so the dead release their beds. */
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    /** Roles currently filled, for deciding what the place still needs. */
    private final Map<NpcRole, Integer> roleCounts = new EnumMap<>(NpcRole.class);

    /** Guard posts, meeting point, shrine and the rest of the shared geography. */
    private final Map<String, BlockPos> landmarks = new HashMap<>();

    /** Gaps in the perimeter that builders and guards are working on. */
    private final List<BlockPos> weakPoints = new ArrayList<>();

    /**
     * How much of the perimeter has been walled and lit, in [0,1]. Raids scale
     * against this, so a settlement that has been left to itself for a few
     * in-game weeks is genuinely harder to overrun than a fresh one.
     */
    private float defence;

    /** 0 calm, 1 under attack right now. Decays when nothing is hostile. */
    private float threat;

    /** Shared larder. Feeds the population; a shortage makes everyone miserable. */
    private int foodStores;
    /** Timber, stone and iron pooled for defences. */
    private int materialStores;

    /** Day the last raid landed, so raids do not stack up back to back. */
    private long lastRaidDay = -1L;

    /** Running prosperity, which drives shop stock and what quests pay. */
    private float prosperity = 0.5F;

    public Settlement(UUID id, String name, BlockPos center, int radius) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.radius = radius;
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BlockPos center() {
        return this.center;
    }

    public void setCenter(BlockPos center) {
        this.center = center;
    }

    public int radius() {
        return this.radius;
    }

    public void setRadius(int radius) {
        this.radius = Mth.clamp(radius, 16, 256);
    }

    public boolean contains(BlockPos pos) {
        return pos.distSqr(this.center) <= (double) this.radius * this.radius;
    }

    public int population() {
        return this.residents.size();
    }

    public Set<UUID> residents() {
        return this.residents;
    }

    public float defence() {
        return this.defence;
    }

    public float threat() {
        return this.threat;
    }

    public float prosperity() {
        return this.prosperity;
    }

    public int foodStores() {
        return this.foodStores;
    }

    public int materialStores() {
        return this.materialStores;
    }

    public long lastRaidDay() {
        return this.lastRaidDay;
    }

    public void setLastRaidDay(long day) {
        this.lastRaidDay = day;
    }

    // ------------------------------------------------------------- membership

    public void addResident(UUID npc, NpcRole role, long gameTime) {
        if (this.residents.add(npc)) {
            this.roleCounts.merge(role, 1, Integer::sum);
        }
        this.lastSeen.put(npc, gameTime);
    }

    public void removeResident(UUID npc, NpcRole role) {
        if (this.residents.remove(npc)) {
            this.roleCounts.computeIfPresent(role, (k, v) -> v <= 1 ? null : v - 1);
        }
        this.homes.remove(npc);
        this.workstations.remove(npc);
        this.lastSeen.remove(npc);
    }

    public void touch(UUID npc, long gameTime) {
        if (this.residents.contains(npc)) {
            this.lastSeen.put(npc, gameTime);
        }
    }

    public int countOf(NpcRole role) {
        return this.roleCounts.getOrDefault(role, 0);
    }

    /**
     * Which role the place most needs filled next.
     *
     * <p>Weighted by ordinary demography, but bent hard by circumstance: a
     * settlement that keeps being raided starts producing guards instead of
     * bakers, and one that has run its larder down produces farmers. That is
     * what makes a village that survives a bad winter look different afterwards.
     */
    public NpcRole neededRole(net.minecraft.util.RandomSource random) {
        int defenders = 0;
        int producers = 0;
        for (Map.Entry<NpcRole, Integer> entry : this.roleCounts.entrySet()) {
            if (entry.getKey().isDefender()) {
                defenders += entry.getValue();
            }
            if (entry.getKey().category() == NpcRole.Category.COMMONER) {
                producers += entry.getValue();
            }
        }
        int people = Math.max(1, population());

        boolean underDefended = defenders * 4 < people || this.threat > 0.35F;
        boolean underFed = this.foodStores < people * 4;

        if (underDefended && random.nextFloat() < 0.7F) {
            return random.nextBoolean() ? NpcRole.GUARD : NpcRole.SOLDIER;
        }
        if (underFed && producers * 2 < people && random.nextFloat() < 0.7F) {
            return random.nextBoolean() ? NpcRole.FARMER : NpcRole.SHEPHERD;
        }
        return NpcRole.randomForVillage(random);
    }

    // ------------------------------------------------------------- claims

    /** Claims a bed for an NPC unless somebody still living holds it. */
    public boolean claimHome(UUID npc, BlockPos pos, long gameTime) {
        return claim(this.homes, npc, pos, gameTime);
    }

    /** Claims a workstation. Same rules as a bed. */
    public boolean claimWorkstation(UUID npc, BlockPos pos, long gameTime) {
        return claim(this.workstations, npc, pos, gameTime);
    }

    private boolean claim(Map<UUID, BlockPos> claims, UUID npc, BlockPos pos, long gameTime) {
        for (Map.Entry<UUID, BlockPos> entry : claims.entrySet()) {
            if (!entry.getKey().equals(npc) && entry.getValue().equals(pos)
                    && !claimExpired(entry.getKey(), gameTime)) {
                return false;
            }
        }
        claims.put(npc, pos);
        this.lastSeen.put(npc, gameTime);
        return true;
    }

    private boolean claimExpired(UUID npc, long gameTime) {
        Long seen = this.lastSeen.get(npc);
        return seen == null || gameTime - seen > CLAIM_EXPIRY_TICKS;
    }

    public BlockPos homeOf(UUID npc) {
        return this.homes.get(npc);
    }

    public BlockPos workstationOf(UUID npc) {
        return this.workstations.get(npc);
    }

    public void releaseClaims(UUID npc) {
        this.homes.remove(npc);
        this.workstations.remove(npc);
    }

    /** True when the position is somebody else's bed or bench. */
    public boolean isClaimed(BlockPos pos, UUID by, long gameTime) {
        return heldByAnother(this.homes, pos, by, gameTime)
                || heldByAnother(this.workstations, pos, by, gameTime);
    }

    private boolean heldByAnother(Map<UUID, BlockPos> claims, BlockPos pos, UUID by, long gameTime) {
        for (Map.Entry<UUID, BlockPos> entry : claims.entrySet()) {
            if (!entry.getKey().equals(by) && entry.getValue().equals(pos)
                    && !claimExpired(entry.getKey(), gameTime)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- geography

    public void setLandmark(String key, BlockPos pos) {
        this.landmarks.put(key, pos);
    }

    public BlockPos landmark(String key) {
        return this.landmarks.get(key);
    }

    public BlockPos landmarkOrCenter(String key) {
        return this.landmarks.getOrDefault(key, this.center);
    }

    public List<BlockPos> weakPoints() {
        return this.weakPoints;
    }

    /** Records a gap in the perimeter for a builder to close. */
    public void reportWeakPoint(BlockPos pos) {
        if (this.weakPoints.size() >= 32) {
            return;
        }
        for (BlockPos known : this.weakPoints) {
            if (known.distSqr(pos) < 9.0D) {
                return;
            }
        }
        this.weakPoints.add(pos.immutable());
    }

    public void clearWeakPoint(BlockPos pos) {
        this.weakPoints.removeIf(p -> p.distSqr(pos) < 4.0D);
        this.defence = Mth.clamp(this.defence + 0.02F, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------- stores

    public void addFood(int amount) {
        this.foodStores = Mth.clamp(this.foodStores + amount, 0, 4096);
    }

    public void addMaterials(int amount) {
        this.materialStores = Mth.clamp(this.materialStores + amount, 0, 4096);
    }

    /** Spends materials on a defence, if there are any. */
    public boolean spendMaterials(int amount) {
        if (this.materialStores < amount) {
            return false;
        }
        this.materialStores -= amount;
        return true;
    }

    public void raiseThreat(float amount) {
        this.threat = Mth.clamp(this.threat + amount, 0.0F, 1.0F);
    }

    public void setDefence(float value) {
        this.defence = Mth.clamp(value, 0.0F, 1.0F);
    }

    /**
     * One settlement tick — called far less often than an entity tick, so
     * everything here is written in "per bookkeeping pass" terms rather than per
     * game tick.
     */
    public void tick(long gameTime, int hostilesNearby) {
        if (hostilesNearby > 0) {
            raiseThreat(0.08F * Math.min(6, hostilesNearby));
        } else {
            this.threat = Math.max(0.0F, this.threat - 0.02F);
        }

        // The place eats. A larder that runs dry costs prosperity, which is what
        // eventually shows up as empty shop shelves and stingier quest rewards.
        int mouths = Math.max(1, population());
        if (this.foodStores >= mouths) {
            this.foodStores -= mouths;
            this.prosperity = Mth.clamp(this.prosperity + 0.004F, 0.0F, 1.0F);
        } else {
            this.foodStores = 0;
            this.prosperity = Mth.clamp(this.prosperity - 0.02F, 0.0F, 1.0F);
        }

        // Walls decay if nobody is maintaining them.
        this.defence = Mth.clamp(this.defence - 0.001F, 0.0F, 1.0F);

        // Claims held by the long dead are released, so a rebuilt village can
        // reuse the beds of the one before it.
        this.lastSeen.entrySet().removeIf(entry -> gameTime - entry.getValue() > CLAIM_EXPIRY_TICKS * 2);
        this.homes.keySet().removeIf(npc -> !this.lastSeen.containsKey(npc));
        this.workstations.keySet().removeIf(npc -> !this.lastSeen.containsKey(npc));
    }

    // ------------------------------------------------------------- persistence

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Name", this.name);
        tag.put("Center", NbtUtils.writeBlockPos(this.center));
        tag.putInt("Radius", this.radius);
        tag.putFloat("Defence", this.defence);
        tag.putFloat("Threat", this.threat);
        tag.putFloat("Prosperity", this.prosperity);
        tag.putInt("Food", this.foodStores);
        tag.putInt("Materials", this.materialStores);
        tag.putLong("LastRaidDay", this.lastRaidDay);

        ListTag residentList = new ListTag();
        for (UUID resident : this.residents) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", resident);
            Long seen = this.lastSeen.get(resident);
            entry.putLong("Seen", seen == null ? 0L : seen);
            BlockPos home = this.homes.get(resident);
            if (home != null) {
                entry.put("Home", NbtUtils.writeBlockPos(home));
            }
            BlockPos work = this.workstations.get(resident);
            if (work != null) {
                entry.put("Work", NbtUtils.writeBlockPos(work));
            }
            residentList.add(entry);
        }
        tag.put("Residents", residentList);

        CompoundTag roles = new CompoundTag();
        this.roleCounts.forEach((role, count) -> roles.putInt(role.key(), count));
        tag.put("Roles", roles);

        CompoundTag marks = new CompoundTag();
        this.landmarks.forEach((key, pos) -> marks.put(key, NbtUtils.writeBlockPos(pos)));
        tag.put("Landmarks", marks);

        ListTag weak = new ListTag();
        for (BlockPos pos : this.weakPoints) {
            weak.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("WeakPoints", weak);
        return tag;
    }

    public static Settlement load(CompoundTag tag) {
        Settlement settlement = new Settlement(
                tag.getUUID("Id"),
                tag.getString("Name"),
                NbtUtils.readBlockPos(tag.getCompound("Center")),
                tag.getInt("Radius"));
        settlement.defence = tag.getFloat("Defence");
        settlement.threat = tag.getFloat("Threat");
        settlement.prosperity = tag.getFloat("Prosperity");
        settlement.foodStores = tag.getInt("Food");
        settlement.materialStores = tag.getInt("Materials");
        settlement.lastRaidDay = tag.getLong("LastRaidDay");

        ListTag residentList = tag.getList("Residents", Tag.TAG_COMPOUND);
        for (int i = 0; i < residentList.size(); i++) {
            CompoundTag entry = residentList.getCompound(i);
            UUID resident = entry.getUUID("Id");
            settlement.residents.add(resident);
            settlement.lastSeen.put(resident, entry.getLong("Seen"));
            if (entry.contains("Home")) {
                settlement.homes.put(resident, NbtUtils.readBlockPos(entry.getCompound("Home")));
            }
            if (entry.contains("Work")) {
                settlement.workstations.put(resident, NbtUtils.readBlockPos(entry.getCompound("Work")));
            }
        }

        CompoundTag roles = tag.getCompound("Roles");
        for (String key : roles.getAllKeys()) {
            settlement.roleCounts.put(NpcRole.byName(key), roles.getInt(key));
        }

        CompoundTag marks = tag.getCompound("Landmarks");
        for (String key : marks.getAllKeys()) {
            settlement.landmarks.put(key, NbtUtils.readBlockPos(marks.getCompound(key)));
        }

        ListTag weak = tag.getList("WeakPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < weak.size(); i++) {
            settlement.weakPoints.add(NbtUtils.readBlockPos(weak.getCompound(i)));
        }
        return settlement;
    }
}
