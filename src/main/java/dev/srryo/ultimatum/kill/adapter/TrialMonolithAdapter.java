package dev.srryo.ultimatum.kill.adapter;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.kill.KillAdapter;
import dev.srryo.ultimatum.kill.KillService;
import dev.srryo.ultimatum.kill.ReflectionAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

public final class TrialMonolithAdapter implements KillAdapter {
    private static final String PACKAGE_PREFIX = "io.github.kosianodangoo.trialmonolith.";
    private static final String LOOT_EMITTED_TAG = "ultimatum:trial_loot_emitted";

    @Override
    public boolean supports(Entity target) {
        return target.getClass().getName().startsWith(PACKAGE_PREFIX);
    }

    @Override
    public void execute(ServerLevel level, Entity target, ServerPlayer attacker, KillService service)
            throws Exception {
        String className = target.getClass().getName();
        if (className.endsWith("TesseractBeastProxyEntity")) {
            removeTesseractController(target);
            enableRemovalBypass(target);
            service.markAndErase(level, target, 200);
            return;
        }
        enableRemovalBypass(target);
        if (className.endsWith("TrialMonolithEntity")) {
            ReflectionAccess.put(target, true, "disableDamageCap");
            if (service.tryForcedDeath(level, target, attacker)) {
                hideBossEvent(target);
                return;
            }
        }
        emitOriginalLoot(level, target, className);
        hideBossEvent(target);
        service.markAndErase(level, target, 100);
    }

    @Override
    public void onStandardDeath(ServerLevel level, Entity target, ServerPlayer attacker,
                                KillService service) {
        hideBossEvent(target);
    }

    private static void enableRemovalBypass(Entity target) throws Exception {
        // Trial Monolith cancels both Entity.remove and the entity-manager callback
        // while soul protection is active. Its own packet listener temporarily flips
        // this flag during a normal server removal, so use that same supported path.
        Method setter = ReflectionAccess.findMethod(target.getClass(),
                "the_trial_monolith$setShouldBypass", boolean.class);
        if (setter != null) {
            setter.setAccessible(true);
            setter.invoke(target, true);
            return;
        }

        // Field fallback keeps this compatible if a release changes the interface
        // dispatch while retaining the injected state used by cannotBeRemoved().
        ReflectionAccess.put(target, true, "the_trial_monolith$shouldBypass");
    }

    private static void removeTesseractController(Entity proxy) throws Exception {
        Object controller = ReflectionAccess.get(proxy, "controller");
        if (controller == null) {
            return;
        }

        // The Trial Monolith's own controller method enables its internal bypass before
        // discarding the proxy and queues the controller for removal from its handler.
        Method remove = ReflectionAccess.findMethod(controller.getClass(), "remove");
        if (remove != null) {
            remove.setAccessible(true);
            remove.invoke(controller);
        }

        ReflectionAccess.put(controller, true, "removed");
        hideBossEvent(controller);
    }

    private static void emitOriginalLoot(ServerLevel level, Entity target, String className) {
        if (target.getPersistentData().getBoolean(LOOT_EMITTED_TAG)) {
            return;
        }
        target.getPersistentData().putBoolean(LOOT_EMITTED_TAG, true);

        if (className.endsWith("TrialMonolithEntity")) {
            spawnItem(level, target, "monolith_fragment", 4);
        } else if (className.endsWith("InvaderMonolithEntity")) {
            spawnItem(level, target, "monolith_fragment", 64);
            spawnItem(level, target, "over_clocker", 1);
            spawnItem(level, target, "high_dimensional_barrier", 1);
        }
    }

    private static void spawnItem(ServerLevel level, Entity target, String path, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("the_trial_monolith", path));
        if (item == null || item == Items.AIR) {
            UltimatumMod.LOGGER.warn("Could not resolve Trial Monolith loot item {}", path);
            return;
        }

        int remaining = count;
        int maximum = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maximum);
            ItemEntity drop = new ItemEntity(level, target.getX(), target.getY() + 0.5D,
                    target.getZ(), new ItemStack(item, stackSize));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
            remaining -= stackSize;
        }
    }

    private static void hideBossEvent(Object owner) {
        Object bossEvent = ReflectionAccess.get(owner, "bossEvent");
        if (bossEvent instanceof ServerBossEvent serverBossEvent) {
            serverBossEvent.setVisible(false);
            serverBossEvent.removeAllPlayers();
            return;
        }
        if (bossEvent != null) {
            try {
                Method visible = ReflectionAccess.findMethod(bossEvent.getClass(), "setVisible", boolean.class);
                if (visible != null) {
                    visible.invoke(bossEvent, false);
                }
                Method removePlayers = ReflectionAccess.findMethod(bossEvent.getClass(), "removeAllPlayers");
                if (removePlayers != null) {
                    removePlayers.invoke(bossEvent);
                }
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Could not hide Trial Monolith boss bar", error);
            }
        }
    }

    @Override
    public String name() {
        return "Trial Monolith/Tesseract";
    }
}
