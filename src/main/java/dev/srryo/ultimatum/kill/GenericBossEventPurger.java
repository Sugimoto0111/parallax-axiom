package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Finds boss events owned by an entity or one of its small controller objects without
 * knowing the other mod's package or field names. This deliberately runs only after all
 * death attempts failed and the entity is being erased.
 */
final class GenericBossEventPurger {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_VISITED = 512;

    private final ServerLevel level;
    private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<UUID> removedEvents = new java.util.HashSet<>();
    private boolean removeEvents = true;

    GenericBossEventPurger(ServerLevel level) {
        this.level = level;
    }

    void purge(Entity target) {
        removeEvents = true;
        visit(target, 0, true);
    }

    void purgeObject(Object owner) {
        removeEvents = true;
        visit(owner, 0, true);
    }

    /** Sets every discoverable boss event to zero without removing it from clients. */
    void zeroProgress(Entity target) {
        removeEvents = false;
        visit(target, 0, true);
    }

    private void visit(Object value, int depth, boolean entityRoot) {
        if (value == null || depth > MAX_DEPTH || visited.size() >= MAX_VISITED
                || !visited.add(value) || isLeaf(value)) {
            return;
        }
        if (value instanceof ServerBossEvent event) {
            remove(event);
            return;
        }
        if (value instanceof Level || value instanceof MinecraftServer || value instanceof ServerPlayer) {
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            if (!type.getComponentType().isPrimitive()) {
                int length = Math.min(Array.getLength(value), 256);
                for (int index = 0; index < length; index++) {
                    visit(Array.get(value, index), depth + 1, false);
                }
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : snapshot(map.entrySet())) {
                visit(entry.getKey(), depth + 1, false);
                visit(entry.getValue(), depth + 1, false);
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object child : snapshot(collection)) {
                visit(child, depth + 1, false);
            }
            return;
        }
        if (!entityRoot && !isLikelyModController(type)) {
            return;
        }

        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object child = read(field, value);
                if (child instanceof ServerBossEvent event) {
                    remove(event);
                } else {
                    visit(child, depth + 1, false);
                }
            }
        }
    }

    private void remove(ServerBossEvent event) {
        UUID id = event.getId();
        if (!removedEvents.add(id)) {
            return;
        }
        if (!removeEvents) {
            try {
                event.setProgress(0.0F);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Boss event rejected zero progress: {}", id, error);
            }
            return;
        }
        try {
            event.setVisible(false);
            event.removeAllPlayers();
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Boss event rejected normal removal: {}", id, error);
        }
        ClientboundBossEventPacket packet = ClientboundBossEventPacket.createRemovePacket(id);
        for (ServerPlayer player : level.players()) {
            try {
                player.connection.send(packet);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Could not force-remove boss event {} for {}",
                        id, player.getGameProfile().getName(), error);
            }
        }
    }

    private static boolean isLikelyModController(Class<?> type) {
        String name = type.getName();
        return !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("sun.")
                && !name.startsWith("com.mojang.")
                && !name.startsWith("net.minecraftforge.")
                && !name.startsWith("net.minecraft.");
    }

    private static boolean isLeaf(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof UUID
                || value instanceof Class<?>;
    }

    private static Object read(Field field, Object owner) {
        try {
            return ReflectionAccess.unsafe().getObject(owner,
                    ReflectionAccess.unsafe().objectFieldOffset(field));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static <T> Collection<T> snapshot(Collection<T> source) {
        try {
            return new ArrayList<>(source);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }
}
