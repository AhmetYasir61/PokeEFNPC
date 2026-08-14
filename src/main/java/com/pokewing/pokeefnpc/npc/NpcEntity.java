package com.pokewing.pokeefnpc.npc;

import com.pokewing.pokeefnpc.PokeEFNPC;
import com.pokewing.pokeefnpc.ai.NpcClaimSitesGoal;
import com.pokewing.pokeefnpc.ai.NpcDefendGoal;
import com.pokewing.pokeefnpc.ai.NpcEmoteGoal;
import com.pokewing.pokeefnpc.ai.NpcFleeThreatGoal;
import com.pokewing.pokeefnpc.ai.NpcFortifyGoal;
import com.pokewing.pokeefnpc.ai.NpcGreetGoal;
import com.pokewing.pokeefnpc.ai.NpcScheduleGoal;
import com.pokewing.pokeefnpc.ai.NpcStealGoal;
import com.pokewing.pokeefnpc.compat.EpicFightBridge;
import com.pokewing.pokeefnpc.emote.Emote;
import com.pokewing.pokeefnpc.menu.MenuPage;
import com.pokewing.pokeefnpc.menu.NpcMenu;
import com.pokewing.pokeefnpc.net.OpenNpcMenuPacket;
import com.pokewing.pokeefnpc.net.PokeEFNPCNetwork;
import com.pokewing.pokeefnpc.quest.QuestBoard;
import com.pokewing.pokeefnpc.schedule.Activity;
import com.pokewing.pokeefnpc.settlement.Settlement;
import com.pokewing.pokeefnpc.settlement.SettlementManager;
import com.pokewing.pokeefnpc.shop.ShopStock;
import com.pokewing.pokeefnpc.shop.ShopTables;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A person.
 *
 * <p>Everything that makes one of these feel inhabited rather than placed is
 * split out and composed here: {@link NpcRole} says what it is, {@link Personality}
 * what it is like, {@link Mood} what it is feeling, {@link Reputation} what it
 * thinks of you, {@link Settlement} where it belongs, and the goals in
 * {@code ai} carry out the day. This class owns none of that logic — it owns the
 * wiring, the synchronisation and the persistence.
 *
 * <p>The face is the part worth understanding. Mood is simulated on the server,
 * where the events that cause it happen, and only the <i>result</i> is put on the
 * wire: the emotion, and the four blendshape channels that render it. The client
 * then hands those to PokeFace if PokeFace is installed. So the emotional state
 * is authoritative and shared — every player sees the same shopkeeper scowling at
 * the same thief — while the drawing of it stays entirely optional.
 */
public class NpcEntity extends PathfinderMob implements Npc, MenuProvider {

    private static final EntityDataAccessor<Integer> DATA_ROLE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_EMOTION =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_INTENSITY =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BROW =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MOUTH_OPEN =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MOUTH_SMILE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_EMOTE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /** Ticks left on the current emote; -1 means "loop until told otherwise". */
    private static final EntityDataAccessor<Integer> DATA_EMOTE_TICKS =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTIVITY =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.INT);
    /**
     * The account name whose skin this character wears, or empty for the role's
     * own skin. Only the NAME travels — each client resolves and downloads the
     * image itself, exactly as vanilla does for players, so no skin data passes
     * through the server.
     */
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);

    /** How often reputation and settlement bookkeeping run, in ticks. */
    private static final int SLOW_TICK = 200;

    private NpcRole role = NpcRole.VILLAGER;
    private Personality personality = new Personality();
    private Mood mood = new Mood();
    private Reputation reputation = new Reputation();
    private ShopStock stock = new ShopStock();
    private QuestBoard questBoard = new QuestBoard();

    @Nullable
    private UUID settlementId;
    @Nullable
    private BlockPos homePos;
    @Nullable
    private BlockPos workPos;

    private Activity activity = Activity.HOME_IDLE;

    /** Set by an operator so the NPC keeps the role and never re-rolls it. */
    private boolean locked;

    /** Set when this NPC was born into the world rather than placed by hand. */
    private boolean natural;

    private int slowTickOffset;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        this.slowTickOffset = level.random.nextInt(SLOW_TICK);
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Deliberately modest defaults: the real numbers come from the role and
        // are applied in applyRole(), so one entity type covers every character.
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ROLE, NpcRole.VILLAGER.ordinal());
        this.entityData.define(DATA_EMOTION, Emotion.NEUTRAL.ordinal());
        this.entityData.define(DATA_INTENSITY, 0.0F);
        this.entityData.define(DATA_BROW, 0.0F);
        this.entityData.define(DATA_MOUTH_OPEN, 0.0F);
        this.entityData.define(DATA_MOUTH_SMILE, 0.0F);
        this.entityData.define(DATA_EMOTE, -1);
        this.entityData.define(DATA_EMOTE_TICKS, 0);
        this.entityData.define(DATA_ACTIVITY, Activity.HOME_IDLE.ordinal());
        this.entityData.define(DATA_SKIN, "");
    }

    // ------------------------------------------------------------------- goals

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Self-preservation outranks the routine: a farmer with a zombie on it
        // stops farming. Which NPCs run and which stand is decided inside the
        // goal from courage, not by which goals were registered.
        this.goalSelector.addGoal(1, new NpcFleeThreatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(3, new NpcStealGoal(this));
        this.goalSelector.addGoal(4, new NpcGreetGoal(this));
        this.goalSelector.addGoal(5, new NpcClaimSitesGoal(this));
        this.goalSelector.addGoal(6, new NpcFortifyGoal(this));
        // The schedule is the backbone — everything above it is an interruption.
        this.goalSelector.addGoal(7, new NpcScheduleGoal(this));
        this.goalSelector.addGoal(8, new NpcEmoteGoal(this));
        this.goalSelector.addGoal(9, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(10, new MoveThroughVillageGoal(this, 0.6D, false, 4, () -> false));
        this.goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(13, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(NpcEntity.class));
        this.targetSelector.addGoal(2, new NpcDefendGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Monster.class,
                10, true, false, this::willFight));
    }

    /**
     * Whether this character would take on that monster. A knight will; a baker
     * will not, and the baker's flee goal picks it up instead. Courage is scaled
     * by how much of a beating the NPC has already taken, so even brave
     * characters break off when they are nearly dead.
     */
    private boolean willFight(LivingEntity target) {
        if (!this.role.isDefender() && this.personality.courage < 0.6F) {
            return false;
        }
        float healthFraction = getHealth() / getMaxHealth();
        return this.personality.standsGround(1.0F - healthFraction * 0.75F);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        tickMood();
        tickEmote();
        if ((this.tickCount + this.slowTickOffset) % SLOW_TICK == 0) {
            slowTick();
        }
    }

    private void tickMood() {
        if (this.mood.tick(this.personality)) {
            // The face only goes on the wire when it actually changed, so a
            // square full of contented villagers costs nothing to keep in sync.
            pushFaceToClients();
        } else if (this.mood.isSpeaking() && this.tickCount % 2 == 0) {
            // ... except while talking, when the mouth needs to keep moving.
            this.entityData.set(DATA_MOUTH_OPEN, this.mood.mouthOpen(this.tickCount));
        }
    }

    private void pushFaceToClients() {
        Emotion emotion = this.mood.current();
        this.entityData.set(DATA_EMOTION, emotion.ordinal());
        this.entityData.set(DATA_INTENSITY, this.mood.intensity());
        this.entityData.set(DATA_BROW, this.mood.brow());
        this.entityData.set(DATA_MOUTH_SMILE, this.mood.mouthSmile());
        this.entityData.set(DATA_MOUTH_OPEN, this.mood.mouthOpen(this.tickCount));
    }

    private void tickEmote() {
        int remaining = this.entityData.get(DATA_EMOTE_TICKS);
        if (remaining > 0) {
            this.entityData.set(DATA_EMOTE_TICKS, remaining - 1);
            if (remaining == 1) {
                this.entityData.set(DATA_EMOTE, -1);
            }
        }
    }

    private void slowTick() {
        this.reputation.decay(this.personality);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Settlement settlement = settlement();
        if (settlement != null) {
            settlement.touch(getUUID(), serverLevel.getGameTime());
            // A settlement under attack is felt by everyone in it, not only by
            // whoever can see the monsters. That shared dread is what makes a
            // raid read on every face in the village at once.
            if (settlement.threat() > 0.3F) {
                this.mood.feel(this.personality, -0.25F * settlement.threat(),
                        0.5F * settlement.threat());
            } else if (settlement.prosperity() > 0.7F) {
                this.mood.feel(this.personality, 0.05F, 0.0F);
            }
            if (settlement.foodStores() <= 0) {
                this.mood.feel(this.personality, -0.15F, -0.1F);
            }
        }
        assignSkinIfNeeded();
        this.stock.restock(this.random, this.role, settlement == null ? 0.5F : settlement.prosperity());
        this.questBoard.refresh(this.random, this.role, serverLevel.getGameTime(),
                settlement == null ? 0.5F : settlement.prosperity());
    }

    // ------------------------------------------------------------- role setup

    /** Applies a role's statistics, gear and personality centres. */
    public void applyRole(NpcRole role, boolean rollPersonality) {
        this.role = role;
        this.entityData.set(DATA_ROLE, role.ordinal());

        setAttribute(Attributes.MAX_HEALTH, role.maxHealth());
        setAttribute(Attributes.ATTACK_DAMAGE, role.attackDamage());
        setAttribute(Attributes.ARMOR, role.armor());
        setAttribute(Attributes.MOVEMENT_SPEED, role.movementSpeed());
        setHealth(getMaxHealth());

        if (rollPersonality) {
            this.personality = Personality.roll(this.random, role, 0.4F);
        }
        if (role.mainHand() != Items.AIR) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(role.mainHand()));
            setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        } else {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        this.stock = ShopTables.freshStock(this.random, role);
        this.questBoard = new QuestBoard();

        // Epic Fight, if it is here, needs to be told this mob exists before it
        // will give it a combat patch. Harmless and silent when it is not.
        EpicFightBridge.registerPatch(this);
    }

    private void setAttribute(net.minecraft.world.entity.ai.attributes.Attribute attribute,
                              double value) {
        var instance = getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data,
                                        @Nullable CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (!this.locked) {
            // Placed by an operator without a role chosen? Take one the local
            // settlement is short of, so hand-placing still produces a plausible
            // town rather than fifteen bakers.
            Settlement settlement = level instanceof ServerLevel serverLevel
                    ? SettlementManager.get(serverLevel).nearest(blockPosition(), 96) : null;
            NpcRole rolled = settlement != null
                    ? settlement.neededRole(this.random) : NpcRole.randomForVillage(this.random);
            applyRole(rolled, true);
        }
        if (!hasCustomName()) {
            setCustomName(Component.literal(NpcNames.pick(this.random, this.role)));
        }
        joinNearestSettlement();
        return result;
    }

    /** Attaches this NPC to whatever settlement it is standing in, if any. */
    public void joinNearestSettlement() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SettlementManager manager = SettlementManager.get(serverLevel);
        Settlement settlement = manager.getOrFound(serverLevel, blockPosition());
        if (settlement == null) {
            settlement = manager.nearest(blockPosition(), 128);
        }
        if (settlement != null) {
            settlement.addResident(getUUID(), this.role, serverLevel.getGameTime());
            this.settlementId = settlement.id();
        }
    }

    // ------------------------------------------------------------- interaction

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || level().isClientSide) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (isSleeping() || !this.activity.interruptible()) {
            // Waking someone up does not endear you to them.
            this.mood.latch(this.personality, Emotion.ANGRY, 60);
            pushFaceToClients();
            this.reputation.adjust(player.getUUID(), -1.0F);
            speak(serverPlayer, "pokeefnpc.dialogue.busy");
            return InteractionResult.CONSUME;
        }
        if (!this.reputation.willDealWith(player.getUUID())) {
            this.mood.latch(this.personality, Emotion.ANGRY);
            pushFaceToClients();
            speak(serverPlayer, "pokeefnpc.dialogue.refuse");
            return InteractionResult.CONSUME;
        }

        getLookControl().setLookAt(player, 30.0F, 30.0F);
        greet(player);
        openMenuFor(serverPlayer);
        return InteractionResult.CONSUME;
    }

    /** Plays the role's greeting and warms the mood a little, once per visit. */
    public void greet(Player player) {
        Reputation.Standing standing = this.reputation.standingOf(player.getUUID());
        switch (standing) {
            case REVERED, TRUSTED -> {
                this.mood.latch(this.personality, Emotion.HAPPY);
                playEmote(this.role.greetEmote());
            }
            case FRIENDLY -> {
                this.mood.feel(this.personality, 0.2F, 0.1F);
                playEmote(this.role.greetEmote());
            }
            case WARY, HOSTILE -> {
                this.mood.latch(this.personality, Emotion.FOCUSED, 60);
                playEmote(Emote.CROSS_ARMS);
            }
            case HATED -> this.mood.latch(this.personality, Emotion.ANGRY);
            default -> this.mood.feel(this.personality, 0.05F, 0.05F);
        }
        pushFaceToClients();
    }

    public void openMenuFor(ServerPlayer player) {
        this.mood.speakFor(60);
        // The container carries the slots; the packet that follows carries
        // everything the screen needs to draw itself as *this* NPC's window —
        // its role, mood, standing, stock, contracts and guild standing.
        net.minecraftforge.network.NetworkHooks.openScreen(player, this,
                buf -> buf.writeVarInt(getId()));
        PokeEFNPCNetwork.sendTo(player, OpenNpcMenuPacket.of(this, player));
    }

    @Override
    public Component getDisplayName() {
        Component name = super.getDisplayName();
        return Component.translatable("pokeefnpc.npc.title", name,
                Component.translatable(this.role.translationKey()));
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                            Player player) {
        return new NpcMenu(containerId, inventory, this);
    }

    /** Sends one line of dialogue and flaps the mouth for as long as it reads. */
    public void speak(ServerPlayer player, String translationKey, Object... args) {
        player.sendSystemMessage(Component.translatable("pokeefnpc.dialogue.line",
                getName(), Component.translatable(translationKey, args)));
        this.mood.speakFor(40);
    }

    // ------------------------------------------------------------- combat

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            this.mood.latch(this.personality, Emotion.HURT);
            if (source.getEntity() instanceof Player player) {
                // Being attacked is the fastest way to lose a village's goodwill,
                // and everybody nearby hears about it.
                this.reputation.adjust(player.getUUID(), -25.0F);
                alertNeighbours(player);
                this.mood.latch(this.personality, this.personality.courage > 0.5F
                        ? Emotion.ANGRY : Emotion.HURT);
            }
            pushFaceToClients();
        }
        return hurt;
    }

    /** Tells everyone within earshot who did it. */
    public void alertNeighbours(Player culprit) {
        var bounds = getBoundingBox().inflate(24.0D);
        for (NpcEntity other : level().getEntitiesOfClass(NpcEntity.class, bounds)) {
            if (other != this) {
                other.witnessed(culprit, -12.0F, Emotion.SURPRISED);
            }
        }
    }

    /** Reacts to something a player did to somebody else. */
    public void witnessed(Player culprit, float reputationDelta, Emotion reaction) {
        this.reputation.adjust(culprit.getUUID(), reputationDelta);
        this.mood.latch(this.personality, reaction, 80);
        pushFaceToClients();
        if (this.role.isDefender() && this.reputation.of(culprit.getUUID()) <= -40.0F) {
            setTarget(culprit);
        }
    }

    @Override
    public void die(DamageSource source) {
        // A death in a small place is felt by everyone in it, and the settlement
        // gives up the dead's bed and bench for the next generation.
        if (!level().isClientSide) {
            for (NpcEntity other : level().getEntitiesOfClass(NpcEntity.class,
                    getBoundingBox().inflate(32.0D))) {
                if (other != this) {
                    other.mood.latch(other.personality, Emotion.SAD);
                    other.pushFaceToClients();
                }
            }
            Settlement settlement = settlement();
            if (settlement != null) {
                settlement.removeResident(getUUID(), this.role);
            }
            com.pokewing.pokeefnpc.brain.NpcBrain.forget(getUUID());
        }
        super.die(source);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // ------------------------------------------------------------- emotes

    /** Plays a gesture once. Looping emotes played this way run one cycle. */
    public void playEmote(Emote emote) {
        if (emote == null) {
            return;
        }
        this.entityData.set(DATA_EMOTE, emote.ordinal());
        this.entityData.set(DATA_EMOTE_TICKS, emote.durationTicks());
        this.mood.feel(this.personality, emote.emotion().valence() * 0.15F, 0.1F);
        EpicFightBridge.playAnimation(this, emote);
    }

    /**
     * Settles into a gesture and holds it — the "loop emote" behaviour. The
     * client repeats the cycle until {@link #stopEmote()} or another emote.
     */
    public void loopEmote(Emote emote) {
        if (emote == null) {
            return;
        }
        if (this.entityData.get(DATA_EMOTE) == emote.ordinal()
                && this.entityData.get(DATA_EMOTE_TICKS) < 0) {
            return;
        }
        this.entityData.set(DATA_EMOTE, emote.ordinal());
        this.entityData.set(DATA_EMOTE_TICKS, -1);
        EpicFightBridge.playAnimation(this, emote);
    }

    public void stopEmote() {
        this.entityData.set(DATA_EMOTE, -1);
        this.entityData.set(DATA_EMOTE_TICKS, 0);
    }

    /** The gesture currently playing, or null. Read by the client animator. */
    @Nullable
    public Emote currentEmote() {
        int ordinal = this.entityData.get(DATA_EMOTE);
        return ordinal < 0 ? null : Emote.byOrdinal(ordinal);
    }

    public boolean isEmoteLooping() {
        return this.entityData.get(DATA_EMOTE_TICKS) < 0;
    }

    // ------------------------------------------------------------- accessors

    public NpcRole role() {
        return level().isClientSide ? NpcRole.byOrdinal(this.entityData.get(DATA_ROLE)) : this.role;
    }

    public Personality personality() {
        return this.personality;
    }

    public Mood mood() {
        return this.mood;
    }

    public Reputation reputation() {
        return this.reputation;
    }

    public ShopStock stock() {
        return this.stock;
    }

    public QuestBoard questBoard() {
        return this.questBoard;
    }

    public Activity activity() {
        return level().isClientSide
                ? Activity.byOrdinal(this.entityData.get(DATA_ACTIVITY)) : this.activity;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
        this.entityData.set(DATA_ACTIVITY, activity.ordinal());
    }

    /** The emotion the client should draw. Safe to call on either side. */
    public Emotion displayedEmotion() {
        return Emotion.byOrdinal(this.entityData.get(DATA_EMOTION));
    }

    public float faceIntensity() {
        return this.entityData.get(DATA_INTENSITY);
    }

    /** Negative is a drawn-down, angry brow; positive is a raised, sad one. */
    public float faceBrow() {
        return this.entityData.get(DATA_BROW);
    }

    public float faceMouthOpen() {
        return this.entityData.get(DATA_MOUTH_OPEN);
    }

    public float faceMouthSmile() {
        return this.entityData.get(DATA_MOUTH_SMILE);
    }

    @Nullable
    public Settlement settlement() {
        if (this.settlementId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return SettlementManager.get(serverLevel).byId(this.settlementId);
    }

    public void setSettlement(@Nullable Settlement settlement) {
        this.settlementId = settlement == null ? null : settlement.id();
    }

    @Nullable
    public BlockPos homePos() {
        return this.homePos;
    }

    public void setHomePos(@Nullable BlockPos pos) {
        this.homePos = pos == null ? null : pos.immutable();
    }

    @Nullable
    public BlockPos workPos() {
        return this.workPos;
    }

    public void setWorkPos(@Nullable BlockPos pos) {
        this.workPos = pos == null ? null : pos.immutable();
    }

    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isNatural() {
        return this.natural;
    }

    public void setNatural(boolean natural) {
        this.natural = natural;
    }

    public boolean offersPage(MenuPage page) {
        return role().hasPage(page);
    }

    // ------------------------------------------------------------- speech

    /**
     * Something a player said to this NPC, whether typed or spoken aloud.
     *
     * <p>Both routes land here so there is exactly one place where being talked
     * to turns into a reply: the NPC looks up, its mood registers the attention,
     * and the request goes to the local model. When no model is running the NPC
     * falls back to its written line, which is why the feature can be off
     * entirely and nothing feels broken.
     *
     * @param spokenAloud true when it came in over voice, so the answer goes back
     *                    out over voice as well
     */
    public void heard(ServerPlayer player, String said, boolean spokenAloud) {
        if (said == null || said.isBlank() || !isAlive()) {
            return;
        }
        getLookControl().setLookAt(player, 30.0F, 30.0F);

        if (!this.reputation.willDealWith(player.getUUID())) {
            this.mood.latch(this.personality, Emotion.ANGRY);
            pushFaceToClients();
            speak(player, "pokeefnpc.dialogue.refuse");
            return;
        }
        // Being addressed is itself a small social event, so an NPC that is
        // talked to warms up a little even before it has answered.
        this.mood.feel(this.personality, 0.08F * this.personality.sociability, 0.1F);

        if (com.pokewing.pokeefnpc.brain.NpcBrain.available()) {
            com.pokewing.pokeefnpc.brain.NpcBrain.converse(this, player, said, spokenAloud);
        } else {
            speak(player, "pokeefnpc.dialogue.no_answer");
        }
    }

    /** Convenience for the voice path. */
    public void hearSpoken(ServerPlayer player, String said) {
        heard(player, said, true);
    }

    // ------------------------------------------------------------- appearance

    /** The account name whose skin this NPC wears, or empty. */
    public String skinName() {
        return this.entityData.get(DATA_SKIN);
    }

    public void setSkinName(@Nullable String name) {
        this.entityData.set(DATA_SKIN, name == null ? "" : name);
    }

    /**
     * Gives an NPC a face from the wider world.
     *
     * <p>Two ways in, checked in that order. A name tag wins — rename a villager
     * to an account name and it wears that account's skin, which is the whole
     * feature with no interface at all. Otherwise one is drawn from the config
     * pool, stably, from the NPC's own id: the same character keeps the same
     * face for its whole life rather than reshuffling every time it is reloaded.
     */
    private void assignSkinIfNeeded() {
        if (com.pokewing.pokeefnpc.PokeEFNPCConfig.skinFromCustomName() && hasCustomName()) {
            String tagged = getCustomName().getString().trim();
            // Only the first word, and only if it could be an account name at
            // all — generated names like "Ser Roderic" are not accounts.
            int space = tagged.indexOf(' ');
            String candidate = space < 0 ? tagged : tagged.substring(0, space);
            if (isPlausibleAccountName(candidate) && !candidate.equalsIgnoreCase(skinName())) {
                setSkinName(candidate);
                return;
            }
        }
        if (!skinName().isEmpty()) {
            return;
        }
        var pool = com.pokewing.pokeefnpc.PokeEFNPCConfig.skinPool();
        if (pool.isEmpty()) {
            return;
        }
        int index = Math.floorMod(getUUID().hashCode(), pool.size());
        setSkinName(pool.get(index));
    }

    private static boolean isPlausibleAccountName(String name) {
        if (name.length() < 3 || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------- misc mob

    @Override
    public boolean removeWhenFarAway(double distance) {
        // People do not evaporate when nobody is looking. A settlement's
        // population has to be stable or none of the bookkeeping means anything.
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.mood.current() == Emotion.ANGRY ? SoundEvents.VILLAGER_NO : SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose,
                                         net.minecraft.world.entity.EntityDimensions size) {
        return 1.62F;
    }

    /**
     * Time of day this NPC thinks it is, in the 0..23 hours the schedule uses.
     * Pulled out so the schedule goal never touches the level directly.
     */
    public int scheduleHour() {
        return com.pokewing.pokeefnpc.schedule.ScheduleTemplate.hourOf(level().getDayTime());
    }

    /** 0..1, how alarmed this NPC's settlement currently is. */
    public float settlementThreat() {
        Settlement settlement = settlement();
        return settlement == null ? 0.0F : settlement.threat();
    }

    // ------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", this.role.key());
        tag.put("Personality", this.personality.save());
        tag.put("Mood", this.mood.save());
        tag.put("Reputation", this.reputation.save());
        tag.put("Stock", this.stock.save());
        tag.put("Quests", this.questBoard.save());
        tag.putInt("Activity", this.activity.ordinal());
        tag.putBoolean("Locked", this.locked);
        tag.putBoolean("Natural", this.natural);
        tag.putString("Skin", skinName());
        if (this.settlementId != null) {
            tag.putUUID("Settlement", this.settlementId);
        }
        if (this.homePos != null) {
            tag.put("Home", net.minecraft.nbt.NbtUtils.writeBlockPos(this.homePos));
        }
        if (this.workPos != null) {
            tag.put("Work", net.minecraft.nbt.NbtUtils.writeBlockPos(this.workPos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // Read the role first: everything below is interpreted in its terms, and
        // applyRole would otherwise overwrite the saved personality.
        NpcRole saved = NpcRole.byName(tag.getString("Role"));
        applyRole(saved, false);
        if (tag.contains("Personality")) {
            this.personality = Personality.load(tag.getCompound("Personality"));
        }
        if (tag.contains("Mood")) {
            this.mood = Mood.load(tag.getCompound("Mood"));
        }
        if (tag.contains("Reputation")) {
            this.reputation = Reputation.load(tag.getCompound("Reputation"));
        }
        if (tag.contains("Stock")) {
            this.stock = ShopStock.load(tag.getCompound("Stock"));
        }
        if (tag.contains("Quests")) {
            this.questBoard = QuestBoard.load(tag.getCompound("Quests"));
        }
        setActivity(Activity.byOrdinal(tag.getInt("Activity")));
        this.locked = tag.getBoolean("Locked");
        this.natural = tag.getBoolean("Natural");
        setSkinName(tag.getString("Skin"));
        this.settlementId = tag.hasUUID("Settlement") ? tag.getUUID("Settlement") : null;
        this.homePos = tag.contains("Home")
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("Home")) : null;
        this.workPos = tag.contains("Work")
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound("Work")) : null;
        // Health was reset to the role maximum by applyRole; restore what vanilla
        // read out of the tag.
        setHealth(Mth.clamp(getHealth(), 1.0F, getMaxHealth()));
        pushFaceToClients();
    }

    static {
        // Fail loudly in dev if the two enums the face bridge relies on ever
        // drift apart, rather than silently drawing the wrong expression.
        if (Emotion.values().length != 8) {
            PokeEFNPC.LOGGER.warn("PokeEFNPC: Emotion has {} entries; the PokeFace atlas expects 8.",
                    Emotion.values().length);
        }
    }
}
