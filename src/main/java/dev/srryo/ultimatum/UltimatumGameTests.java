package dev.srryo.ultimatum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.ForgeMod;

import dev.srryo.ultimatum.kill.ContainerBypass;
import dev.srryo.ultimatum.kill.ReflectionAccess;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
import dev.srryo.ultimatum.ritual.AcquisitionRitualRecipe;
import dev.srryo.ultimatum.ritual.RitualRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@GameTestHolder(UltimatumMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimatumGameTests {
    private UltimatumGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void observerRitualConsumesEveryOffering(GameTestHelper helper) {
        AcquisitionRitualRecipe recipe = helper.getLevel().getRecipeManager()
                .getAllRecipesFor(RitualRegistries.ACQUISITION_TYPE.get()).stream()
                .filter(candidate -> candidate.getId().getPath()
                        .equals("invariant_observer_ritual"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Invariant Observer ritual recipe was not loaded"));
        BlockPos center = helper.absolutePos(new BlockPos(2, 2, 2));
        int entityOffset = 0;
        for (AcquisitionRitualRecipe.Requirement requirement : recipe.requirements()) {
            ItemStack template = requirement.ingredient().getItems()[0].copy();
            int remaining = requirement.count();
            while (remaining > 0) {
                int count = Math.min(remaining, template.getMaxStackSize());
                ItemStack offering = template.copy();
                offering.setCount(count);
                ItemEntity entity = new ItemEntity(helper.getLevel(),
                        center.getX() + 0.3D + entityOffset % 4 * 0.35D,
                        center.getY() + 1.0D,
                        center.getZ() + 0.3D + entityOffset / 4 * 0.25D,
                        offering);
                helper.getLevel().addFreshEntity(entity);
                entityOffset++;
                remaining -= count;
            }
        }

        helper.assertTrue(UltimatumMod.ACQUISITION_RITUAL_SERVICE
                        .tryStartAt(helper.getLevel(), center),
                "Complete Observer offering did not start its ritual");
        helper.runAfterDelay(recipe.duration() + 10, () -> {
            AABB resultArea = AABB.ofSize(Vec3.atCenterOf(center), 8.0D, 8.0D, 8.0D);
            List<ItemEntity> results = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, resultArea,
                    item -> item.getItem().is(UltimatumMod.ABSOLUTE_ARTIFACT.get()));
            helper.assertTrue(results.size() == 1,
                    "Observer ritual did not produce exactly one Invariant Observer");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            resultArea, item -> !item.getItem().is(
                                    UltimatumMod.ABSOLUTE_ARTIFACT.get())).isEmpty(),
                    "Observer ritual left a reserved offering unconsumed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ordinaryMobUsesNormalDeath(GameTestHelper helper) {
        Entity zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        Player attacker = helper.makeMockPlayer();
        UltimatumMod.KILL_SERVICE.enqueue(attacker, zombie);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!zombie.isAlive(), "The ordinary mob survived Absolute End");
            helper.assertTrue(!zombie.isRemoved(),
                    "The ordinary mob skipped its normal death phase and was erased immediately");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void genericDeathKernelBypassesPublicDeathOverrides(GameTestHelper helper) {
        AlwaysAliveResistantZombie target = new AlwaysAliveResistantZombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);
        UUID uuid = target.getUUID();
        UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), target);

        helper.runAfterDelay(5, () -> {
            helper.assertTrue(target.getHealth() == 0.0F,
                    "A hostile getHealth override escaped the universal method hook");
            helper.assertTrue(!target.isAlive() && target.isDeadOrDying(),
                    "Hostile alive/dead overrides escaped the universal method hook");
            helper.assertTrue(target.lootCalls == 1,
                    "The NoSugar-style forced path did not emit death loot exactly once");
        });

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getLevel().getEntity(uuid) == null,
                    "The generic death kernel did not finish a mob that rejected hurt/setHealth/die");
            helper.assertTrue(target.hurtCalls == 0,
                    "The universal death pipeline called a hostile hurt override");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void serverLookFallbackFindsUnpickableMob(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer();
        Vec3 attackerPosition = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        attacker.moveTo(attackerPosition.x, attackerPosition.y, attackerPosition.z,
                0.0F, 0.0F);

        UnpickableResistantZombie target = new UnpickableResistantZombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(1, 2, 5)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);
        UUID uuid = target.getUUID();

        helper.assertTrue(UltimatumMod.KILL_SERVICE.eraseSpecialLookTarget(attacker, 16.0D),
                "The server look fallback did not acquire an unpickable mob");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getLevel().getEntity(uuid) == null,
                    "The unpickable look target survived the universal pipeline");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serverLookFallbackRejectsOffAxisMob(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer();
        Vec3 attackerPosition = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        attacker.moveTo(attackerPosition.x, attackerPosition.y, attackerPosition.z,
                0.0F, 0.0F);

        Zombie target = new Zombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(3, 2, 4)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);

        helper.assertTrue(!UltimatumMod.KILL_SERVICE.eraseSpecialLookTarget(attacker, 16.0D),
                "The server look fallback acquired an ordinary mob outside the view ray");
        helper.assertTrue(target.isAlive(), "The off-axis mob was queued for erasure");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serverLookFallbackStopsAtBlock(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer();
        Vec3 attackerPosition = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        attacker.moveTo(attackerPosition.x, attackerPosition.y, attackerPosition.z,
                0.0F, 0.0F);
        helper.setBlock(new BlockPos(1, 3, 3), Blocks.STONE);

        Zombie target = new Zombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(1, 2, 5)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);

        helper.assertTrue(!UltimatumMod.KILL_SERVICE.eraseSpecialLookTarget(attacker, 16.0D),
                "The server look fallback acquired a mob through a solid block");
        helper.assertTrue(target.isAlive(), "The occluded mob was queued for erasure");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void genericDeepEraseFollowsStalledDeath(GameTestHelper helper) {
        Zombie target = new EntrenchedZombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);
        UUID uuid = target.getUUID();
        UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), target);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(helper.getLevel().getEntity(uuid) == null,
                    "A mob that stalled tickDeath remained after generic deep erasure");
            ReflectionAccess.put(target, null, "removalReason", "f_146795_");
            helper.assertTrue(!helper.getLevel().addWithUUID(target),
                    "A tombstoned mob re-entered the persistent entity indexes");
            Zombie replacement = new Zombie(helper.getLevel());
            replacement.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(replacement),
                    "A new UUID of the same entity type was incorrectly blocked");
            replacement.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void deepEraseFindsWrappedBossEvent(GameTestHelper helper) {
        EntrenchedBossZombie target = new EntrenchedBossZombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);
        UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), target);

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!target.controller.presentation.event.isVisible(),
                    "The generic eraser left a wrapped boss event visible");
            helper.assertTrue(helper.getLevel().getEntity(target.getUUID()) == null,
                    "The wrapped-boss fixture remained in the world index");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void logicalControllerInDirectStaticRegistryIsErased(GameTestHelper helper) {
        DirectLogicalRegistry.CONTROLLERS.clear();
        LogicalProxyZombie proxy = new LogicalProxyZombie(helper.getLevel());
        DirectLogicalController controller = new DirectLogicalController(proxy);
        proxy.controller = controller;
        DirectLogicalRegistry.CONTROLLERS.add(controller);
        proxy.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(proxy);
        UUID uuid = proxy.getUUID();

        UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), proxy);
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(!DirectLogicalRegistry.CONTROLLERS.contains(controller),
                        "A directly registered logical controller survived");
                helper.assertTrue(controller.removed,
                        "The direct logical controller was not marked removed");
                helper.assertTrue(!controller.bossEvent.isVisible(),
                        "The direct logical controller left its boss event visible");
                helper.assertTrue(helper.getLevel().getEntity(uuid) == null,
                        "The direct logical controller's proxy remained indexed");
                helper.succeed();
            } finally {
                DirectLogicalRegistry.CONTROLLERS.clear();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void logicalControllerBehindStaticManagerIsErased(GameTestHelper helper) {
        ManagedLogicalRegistry.HANDLERS.clear();
        LogicalProxyZombie proxy = new LogicalProxyZombie(helper.getLevel());
        ManagedLogicalController controller = new ManagedLogicalController(proxy);
        proxy.controller = controller;
        ManagedLogicalHandler handler = new ManagedLogicalHandler();
        handler.controllers.add(controller);
        ManagedLogicalRegistry.HANDLERS.put("level", handler);
        proxy.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(proxy);
        UUID uuid = proxy.getUUID();

        UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), proxy);
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(!handler.controllers.contains(controller),
                        "A handler-owned logical controller survived");
                helper.assertTrue(controller.removed,
                        "The handler-owned logical controller was not marked removed");
                helper.assertTrue(!controller.bossEvent.isVisible(),
                        "The handler-owned logical controller left its boss event visible");
                helper.assertTrue(helper.getLevel().getEntity(uuid) == null,
                        "The handler-owned logical controller's proxy remained indexed");
                helper.succeed();
            } finally {
                ManagedLogicalRegistry.HANDLERS.clear();
            }
        });
    }

    private static class ResistantZombie extends Zombie {
        int hurtCalls;

        ResistantZombie(Level level) {
            super(level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
            hurtCalls++;
            return false;
        }

        @Override
        public void setHealth(float health) {
            // Simulates a mod that rejects the public health API.
        }

        @Override
        public void die(DamageSource source) {
            // Simulates a mod that replaces/cancels LivingEntity.die().
        }
    }

    private static final class UnpickableResistantZombie extends ResistantZombie {
        UnpickableResistantZombie(Level level) {
            super(level);
        }

        @Override
        public boolean isPickable() {
            return false;
        }
    }

    private static final class AlwaysAliveResistantZombie extends ResistantZombie {
        private int lootCalls;

        AlwaysAliveResistantZombie(Level level) {
            super(level);
        }

        @Override
        public float getHealth() {
            return getMaxHealth();
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public boolean isDeadOrDying() {
            return false;
        }

        @Override
        protected void dropCustomDeathLoot(DamageSource source, int looting,
                                           boolean recentlyHit) {
            lootCalls++;
        }
    }

    private static final class EntrenchedZombie extends ResistantZombie {
        EntrenchedZombie(Level level) {
            super(level);
        }

        @Override
        protected void tickDeath() {
            // Simulates a corpse that deliberately refuses its normal removal phase.
        }
    }

    private static final class EntrenchedBossZombie extends ResistantZombie {
        private final BossController controller = new BossController();

        EntrenchedBossZombie(Level level) {
            super(level);
        }

        @Override
        protected void tickDeath() {
            // Forces the final generic eraser and its controller graph walk.
        }
    }

    private static final class BossController {
        private final BossPresentation presentation = new BossPresentation();
    }

    private static final class BossPresentation {
        private final ServerBossEvent event = new ServerBossEvent(
                Component.literal("Wrapped boss"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
    }

    private static final class LogicalProxyZombie extends Zombie {
        private Object controller;

        LogicalProxyZombie(Level level) {
            super(level);
        }
    }

    private static final class DirectLogicalController {
        private final LogicalProxyZombie proxy;
        private final ServerBossEvent bossEvent = logicalBossEvent("Direct logical boss");
        private boolean removed;

        DirectLogicalController(LogicalProxyZombie proxy) {
            this.proxy = proxy;
        }
    }

    private static final class DirectLogicalRegistry {
        private static final List<DirectLogicalController> CONTROLLERS = new ArrayList<>();
    }

    private static final class ManagedLogicalController {
        private final LogicalProxyZombie proxy;
        private final ServerBossEvent bossEvent = logicalBossEvent("Managed logical boss");
        private boolean removed;

        ManagedLogicalController(LogicalProxyZombie proxy) {
            this.proxy = proxy;
        }
    }

    private static final class ManagedLogicalHandler {
        private final List<ManagedLogicalController> controllers = new ArrayList<>();
    }

    private static final class ManagedLogicalRegistry {
        private static final Map<String, ManagedLogicalHandler> HANDLERS = new HashMap<>();
    }

    private static ServerBossEvent logicalBossEvent(String name) {
        return new ServerBossEvent(Component.literal(name), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void protectedHealthMapCanBeZeroed(GameTestHelper helper) {
        Object entityKey = new Object();
        Map<Object, Float> protectedMap = new RefusingWeakHashMap<>();
        protectedMap.put(entityKey, 20.0F);
        helper.assertTrue(!protectedMap.containsKey(entityKey),
                "The fixture did not reject its public put method");
        helper.assertTrue(ContainerBypass.putMapEntry(protectedMap, entityKey, 0.0F),
                "Container bypass could not write protected health");
        helper.assertTrue(Float.valueOf(0.0F).equals(protectedMap.get(entityKey)),
                "Protected health was not forced to zero");
        helper.succeed();
    }

    private static final class RefusingWeakHashMap<K, V> extends WeakHashMap<K, V> {
        @Override
        public V put(K key, V value) {
            return null;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void equippedAbsoluteArtifactGrantsInvincibility(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        var inventory = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        helper.assertTrue(inventory != null, "Curios did not attach an inventory to the player");
        helper.assertTrue(inventory.getStacksHandler("artifact").isPresent(),
                "The dedicated artifact slot was not assigned to players");

        ItemStack artifact = new ItemStack(UltimatumMod.ABSOLUTE_ARTIFACT.get());
        inventory.setEquippedCurio("artifact", 0, artifact);
        helper.assertTrue(InvincibilityService.hasAbsoluteArtifactEquipped(player),
                "The equipped Absolute Artifact was not detected");
        float originalFlightSpeed = player.getAbilities().getFlyingSpeed();
        UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
        helper.assertTrue(player.getAbilities().mayfly,
                "The equipped Absolute Artifact did not grant survival flight");
        helper.assertTrue(player.getAbilities().getFlyingSpeed()
                        == dev.srryo.ultimatum.mobility.ArtifactMobilityService.NORMAL_FLIGHT_SPEED,
                "The equipped Absolute Artifact did not set its normal flight speed");
        player.setSprinting(true);
        UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
        helper.assertTrue(player.getAbilities().getFlyingSpeed()
                        == dev.srryo.ultimatum.mobility.ArtifactMobilityService.BOOSTED_FLIGHT_SPEED,
                "Sprinting did not engage Absolute Artifact flight boost");
        player.setSprinting(false);
        artifact.getOrCreateTag().putInt(
                dev.srryo.ultimatum.mobility.ArtifactMobilityService.FLIGHT_STAGE_TAG, 2);
        UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
        helper.assertTrue(player.getAbilities().getFlyingSpeed() == 0.25F,
                "The persisted flight-speed stage was not applied");
        helper.assertTrue(player.getAttribute(
                        ForgeMod.STEP_HEIGHT_ADDITION.get()).getModifier(
                        dev.srryo.ultimatum.mobility.ArtifactMobilityService.STEP_ASSIST_ID)
                        != null,
                "The Absolute Artifact did not enable step assist by default");
        artifact.getOrCreateTag().putBoolean(
                dev.srryo.ultimatum.mobility.ArtifactMobilityService.STEP_ASSIST_TAG, false);
        UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
        helper.assertTrue(player.getAttribute(
                        ForgeMod.STEP_HEIGHT_ADDITION.get()).getModifier(
                        dev.srryo.ultimatum.mobility.ArtifactMobilityService.STEP_ASSIST_ID)
                        == null,
                "Disabling step assist left its step-height modifier active");
        artifact.getOrCreateTag().putBoolean(
                dev.srryo.ultimatum.mobility.ArtifactMobilityService.STEP_ASSIST_TAG, true);
        UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
        helper.assertTrue(dev.srryo.ultimatum.mobility.ArtifactMobilityService
                        .isInertiaDisabled(player),
                "Inertia canceling was not enabled by default");
        player.getAbilities().flying = true;
        ItemStack configuredArtifact = artifact.copy();
        configuredArtifact.getOrCreateTag().putBoolean("ConfigurationUpdate", true);
        ((dev.srryo.ultimatum.item.AbsoluteArtifactItem)
                UltimatumMod.ABSOLUTE_ARTIFACT.get()).onUnequip(
                new SlotContext("artifact", player, 0, false, true),
                configuredArtifact, artifact);
        helper.assertTrue(player.getAbilities().flying,
                "An Artifact-to-Artifact configuration update canceled active flight");

        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 3));
        helper.assertTrue(!player.hasEffect(MobEffects.POISON),
                "A harmful effect bypassed Absolute Artifact immunity");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 1));
        player.getActiveEffectsMap().put(MobEffects.WITHER,
                new MobEffectInstance(MobEffects.WITHER, 200, 3));
        InvincibilityService.restoreNow(player);
        helper.assertTrue(!player.hasEffect(MobEffects.WITHER),
                "A direct hostile effect-map write survived the purge");
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED),
                "Debuff immunity incorrectly removed a beneficial effect");
        player.setTicksFrozen(100);
        helper.assertTrue(player.getTicksFrozen() == 0,
                "Freezing bypassed the Absolute Artifact restraint guard");
        player.isInPowderSnow = true;
        player.wasInPowderSnow = true;
        ReflectionAccess.put(player, new Vec3(0.1D, 0.2D, 0.3D),
                "stuckSpeedMultiplier", "f_19865_");
        InvincibilityService.restoreNow(player);
        helper.assertTrue(!player.isInPowderSnow && !player.wasInPowderSnow,
                "Powder-snow restraint state survived restoration");
        helper.assertTrue(Vec3.ZERO.equals(ReflectionAccess.get(player,
                        "stuckSpeedMultiplier", "f_19865_")),
                "A direct stuck-speed multiplier survived restoration");

        artifact.getOrCreateTag().putBoolean(
                dev.srryo.ultimatum.mobility.ArtifactUtilityService.NIGHT_VISION_TAG, true);
        UltimatumMod.ARTIFACT_UTILITY_SERVICE.updateNow(player);
        helper.assertTrue(player.hasEffect(MobEffects.NIGHT_VISION),
                "Enabled Artifact night vision was not applied");
        artifact.getOrCreateTag().putBoolean(
                dev.srryo.ultimatum.mobility.ArtifactUtilityService.NIGHT_VISION_TAG, false);
        UltimatumMod.ARTIFACT_UTILITY_SERVICE.updateNow(player);
        helper.assertTrue(!player.hasEffect(MobEffects.NIGHT_VISION),
                "Disabled Artifact night vision remained active");

        artifact.getOrCreateTag().putBoolean(
                dev.srryo.ultimatum.mobility.ArtifactUtilityService.ITEM_MAGNET_TAG, true);
        ItemEntity dropped = new ItemEntity(helper.getLevel(),
                player.getX() + 8.0D, player.getY(), player.getZ(),
                new ItemStack(Items.DIAMOND));
        helper.getLevel().addFreshEntity(dropped);
        UltimatumMod.ARTIFACT_UTILITY_SERVICE.attractItemsNow(player);
        Vec3 towardPlayer = player.position().add(0.0D, 0.75D, 0.0D)
                .subtract(dropped.position());
        helper.assertTrue(dropped.getDeltaMovement().dot(towardPlayer) > 0.0D,
                "The 64-block item magnet did not pull an item toward the player");
        dropped.discard();

        artifact.getOrCreateTag().putInt(
                dev.srryo.ultimatum.mobility.ArtifactReachService.REACH_STAGE_TAG, 1);
        UltimatumMod.ARTIFACT_REACH_SERVICE.updateNow(player);
        helper.assertTrue(UltimatumMod.ARTIFACT_REACH_SERVICE.reach(player) == 8.0D,
                "The persisted reach stage was not read from the artifact");
        helper.assertTrue(player.getAttributeValue(ForgeMod.BLOCK_REACH.get()) >= 8.0D,
                "The staged block reach modifier was not applied");
        helper.assertTrue(player.getAttributeValue(ForgeMod.ENTITY_REACH.get()) >= 8.0D,
                "The staged entity reach modifier was not applied");

        player.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        player.setHealth(0.0F);
        player.kill();
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(player.isAlive(),
                        "The equipped Absolute Artifact did not reject death");
                helper.assertTrue(!player.isRemoved(),
                        "The equipped Absolute Artifact did not reject removal");
                helper.assertTrue(player.getHealth() == player.getMaxHealth(),
                        "The equipped Absolute Artifact did not restore health");
                inventory.setEquippedCurio("artifact", 0, ItemStack.EMPTY);
                InvincibilityService.release(player);
                UltimatumMod.ARTIFACT_MOBILITY_SERVICE.updateNow(player);
                UltimatumMod.ARTIFACT_REACH_SERVICE.updateNow(player);
                UltimatumMod.ARTIFACT_UTILITY_SERVICE.updateNow(player);
                helper.assertTrue(!player.getAbilities().mayfly,
                        "Removing the artifact left survival flight enabled");
                helper.assertTrue(player.getAbilities().getFlyingSpeed() == originalFlightSpeed,
                        "Removing the artifact did not restore the original flight speed");
                helper.assertTrue(player.getAttribute(ForgeMod.BLOCK_REACH.get())
                                .getModifier(UUID.fromString(
                                        "bdb23715-0f21-4e70-a5fc-76fe4ccfb924")) == null,
                        "Removing the artifact left its reach modifier active");
                helper.assertTrue(player.getAttribute(
                                ForgeMod.STEP_HEIGHT_ADDITION.get()).getModifier(
                                dev.srryo.ultimatum.mobility.ArtifactMobilityService.STEP_ASSIST_ID)
                                == null,
                        "Removing the artifact left step assist active");
                helper.succeed();
            } finally {
                inventory.setEquippedCurio("artifact", 0, ItemStack.EMPTY);
                InvincibilityService.release(player);
                UltimatumMod.ARTIFACT_MOBILITY_SERVICE.disableNow(player);
                UltimatumMod.ARTIFACT_REACH_SERVICE.disableNow(player);
                UltimatumMod.ARTIFACT_UTILITY_SERVICE.disableNow(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void absoluteEndHolderRejectsDeathAndRemoval(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        InvincibilityService.anchor(player);
        player.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
        player.setHealth(0.0F);
        player.kill();
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(player.isAlive(), "Absolute End holder entered a dead state");
                helper.assertTrue(!player.isRemoved(), "Absolute End holder was removed");
                helper.assertTrue(player.getHealth() == player.getMaxHealth(),
                        "Absolute End holder did not recover full health");
                helper.succeed();
            } finally {
                InvincibilityService.release(player);
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void trialSoulDeathCannotEraseHolderWhenInstalled(GameTestHelper helper) {
        try {
            Class<?> helperClass = Class.forName(
                    "io.github.kosianodangoo.trialmonolith.common.helper.EntityHelper", false,
                    UltimatumGameTests.class.getClassLoader());
            Method soulDeath = helperClass.getDeclaredMethod("onSoulDeath", Entity.class);
            Player player = helper.makeMockPlayer();
            InvincibilityService.anchor(player);
            soulDeath.invoke(null, player);
            helper.runAfterDelay(2, () -> assertProtectedPlayer(helper, player,
                    "Trial Monolith soul death"));
        } catch (ClassNotFoundException missing) {
            helper.succeed();
        } catch (Throwable error) {
            helper.fail("Could not run Trial soul-death compatibility test: " + error);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void omniForcedHealthCannotEraseHolderWhenInstalled(GameTestHelper helper) {
        try {
            Class<?> entityUtil = Class.forName("flashfur.omnimobs.util.EntityUtil", false,
                    UltimatumGameTests.class.getClassLoader());
            Method forceHealth = entityUtil.getDeclaredMethod("forceSetHealth",
                    net.minecraft.world.entity.LivingEntity.class, float.class);
            Player player = helper.makeMockPlayer();
            InvincibilityService.anchor(player);
            forceHealth.invoke(null, player, Float.NEGATIVE_INFINITY);
            helper.runAfterDelay(2, () -> assertProtectedPlayer(helper, player,
                    "Omni-Mobs forced health"));
        } catch (ClassNotFoundException missing) {
            helper.succeed();
        } catch (Throwable error) {
            helper.fail("Could not run Omni forced-health compatibility test: " + error);
        }
    }

    private static void assertProtectedPlayer(GameTestHelper helper, Player player, String source) {
        try {
            helper.assertTrue(player.isAlive(), source + " killed the protected player");
            helper.assertTrue(!player.isRemoved(), source + " removed the protected player");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(),
                    source + " corrupted protected health");
            helper.succeed();
        } finally {
            InvincibilityService.release(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void pig2IsErasedWhenInstalled(GameTestHelper helper) {
        ResourceLocation key = new ResourceLocation("pig2mod", "pig2");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            helper.succeed();
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
        Entity pig2 = type.create(helper.getLevel());
        helper.assertTrue(pig2 != null, "Pig2 EntityType returned null");
        pig2.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(pig2);
        Player attacker = helper.makeMockPlayer();
        UltimatumMod.KILL_SERVICE.enqueue(attacker, pig2);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(helper.getLevel().getEntity(pig2.getUUID()) == null,
                    "Pig2 remained in the ServerLevel UUID index");
            helper.assertTrue(pig2.isRemoved(), "Pig2 did not reach a removed state");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    @SuppressWarnings("unchecked")
    public static void metapotentControllerIsErasedWhenInstalled(GameTestHelper helper) {
        ResourceLocation key = new ResourceLocation("omnimobs", "metapotent_flashfur");
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
            helper.succeed();
            return;
        }
        try {
            ClassLoader loader = UltimatumGameTests.class.getClassLoader();
            Class<?> controllerClass = Class.forName(
                    "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfur", false, loader);
            Constructor<?> constructor = controllerClass.getConstructor(Vec3.class, float.class,
                    float.class, net.minecraft.world.level.Level.class);
            Vec3 position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
            Object controller = constructor.newInstance(position, 0.0F, 0.0F, helper.getLevel());

            Class<?> levelClass = Class.forName(
                    "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfurLevel", false, loader);
            Object rawControllers = ReflectionAccess.getStatic(levelClass, "metapotentFlashfurList");
            helper.assertTrue(rawControllers instanceof Collection<?>, "Metapotent controller list is unavailable");
            Collection<Object> controllers = (Collection<Object>) rawControllers;
            ContainerBypass.invokeSuperclass(controllers, "add", new Class<?>[]{Object.class}, controller);

            Entity proxy = (Entity) ReflectionAccess.get(controller, "metapotentFlashfurProxy");
            helper.assertTrue(proxy != null, "Metapotent Flashfur proxy was not created");
            UUID proxyUuid = proxy.getUUID();
            UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), proxy);
            helper.runAfterDelay(15, () -> {
                helper.assertTrue(!controllers.contains(controller),
                        "Metapotent Flashfur remained in its protected controller list");
                helper.assertTrue(helper.getLevel().getEntity(proxyUuid) == null,
                        "Metapotent Flashfur proxy remained in the UUID index");
                helper.succeed();
            });
        } catch (Throwable error) {
            helper.fail("Could not construct Metapotent Flashfur fixture: " + error);
        }
    }
}
