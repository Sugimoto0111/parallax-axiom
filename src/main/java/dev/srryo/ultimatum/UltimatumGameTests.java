package dev.srryo.ultimatum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import dev.srryo.ultimatum.kill.ContainerBypass;
import dev.srryo.ultimatum.kill.ReflectionAccess;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@GameTestHolder(UltimatumMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimatumGameTests {
    private UltimatumGameTests() {
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
        Zombie target = new ResistantZombie(helper.getLevel());
        target.moveTo(helper.absolutePos(new BlockPos(1, 2, 1)), 0.0F, 0.0F);
        helper.getLevel().addFreshEntity(target);
        UUID uuid = target.getUUID();
        UltimatumMod.KILL_SERVICE.enqueue(helper.makeMockPlayer(), target);

        helper.runAfterDelay(30, () -> {
            helper.assertTrue(helper.getLevel().getEntity(uuid) == null,
                    "The generic death kernel did not finish a mob that rejected hurt/setHealth/die");
            helper.succeed();
        });
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

    private static class ResistantZombie extends Zombie {
        ResistantZombie(Level level) {
            super(level);
        }

        @Override
        public boolean hurt(DamageSource source, float amount) {
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

    private static final class EntrenchedZombie extends ResistantZombie {
        EntrenchedZombie(Level level) {
            super(level);
        }

        @Override
        protected void tickDeath() {
            // Simulates a corpse that deliberately refuses its normal removal phase.
        }
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
