package dev.srryo.ultimatum.invincibility;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.kill.ReflectionAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvincibilityService {
    private static final Set<UUID> ANCHORED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> RESTORING = ThreadLocal.withInitial(() -> false);

    public static boolean isProtected(Entity entity) {
        return entity instanceof Player player
                && (isHoldingAbsoluteEnd(player) || ANCHORED_PLAYERS.contains(player.getUUID()));
    }

    public static boolean isHoldingAbsoluteEnd(Player player) {
        if (!UltimatumMod.ABSOLUTE_END.isPresent()) {
            return false;
        }
        try {
            return player.getMainHandItem().is(UltimatumMod.ABSOLUTE_END.get())
                    || player.getOffhandItem().is(UltimatumMod.ABSOLUTE_END.get());
        } catch (Throwable ignored) {
            // Forge's GameTest MockPlayer intentionally has no Inventory instance.
            return false;
        }
    }

    public static void anchor(Player player) {
        ANCHORED_PLAYERS.add(player.getUUID());
    }

    public static void release(Player player) {
        ANCHORED_PLAYERS.remove(player.getUUID());
    }

    public static boolean hasAnyProtectedPlayer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return false;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isProtected(player)) {
                return true;
            }
        }
        return false;
    }

    public static float protectedHealth(LivingEntity entity) {
        float maximum = entity.getMaxHealth();
        return Float.isFinite(maximum) && maximum > 0.0F ? maximum : 20.0F;
    }

    public static void restoreNow(LivingEntity entity) {
        if (!isProtected(entity) || RESTORING.get()) {
            return;
        }
        RESTORING.set(true);
        try {
            Object reason = ReflectionAccess.get(entity, "removalReason", "f_146795_");
            if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
                ReflectionAccess.put(entity, null, "removalReason", "f_146795_");
            }
            ReflectionAccess.put(entity, false, "dead", "f_20890_");
            ReflectionAccess.put(entity, 0, "deathTime", "f_20919_");
            ReflectionAccess.put(entity, 0, "hurtTime", "f_20916_");
            entity.setHealth(protectedHealth(entity));
            entity.setAbsorptionAmount(Math.max(0.0F, entity.getAbsorptionAmount()));
            entity.clearFire();
            entity.setAirSupply(entity.getMaxAirSupply());
            entity.fallDistance = 0.0F;
            if (entity instanceof Player player) {
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(20.0F);
            }
        } finally {
            RESTORING.set(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onAttack(LivingAttackEvent event) {
        cancelAndRestore(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onHurt(LivingHurtEvent event) {
        cancelAndRestore(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDamage(LivingDamageEvent event) {
        cancelAndRestore(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDeath(LivingDeathEvent event) {
        cancelAndRestore(event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onKnockback(LivingKnockBackEvent event) {
        if (isProtected(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (isHoldingAbsoluteEnd(player)) {
            ANCHORED_PLAYERS.add(player.getUUID());
            restoreNow(player);
        } else if (event.phase == TickEvent.Phase.END) {
            ANCHORED_PLAYERS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!isProtected(player)) {
                continue;
            }
            restoreNow(player);
            if (!isHoldingAbsoluteEnd(player)) {
                ANCHORED_PLAYERS.remove(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        release(event.getEntity());
    }

    private static void cancelAndRestore(LivingEntity entity, net.minecraftforge.eventbus.api.Event event) {
        if (isProtected(entity)) {
            event.setCanceled(true);
            restoreNow(entity);
        }
    }

}
