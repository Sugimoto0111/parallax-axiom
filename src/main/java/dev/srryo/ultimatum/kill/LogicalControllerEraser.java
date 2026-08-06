package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Detects logical mobs represented by a non-Entity controller and one or more Entity
 * proxies. A candidate is accepted only when all three independent relationships exist:
 * proxy -> controller, controller -> proxy, and a static registry containing controller.
 * No mod id, class name or field name is used.
 */
final class LogicalControllerEraser {
    private static final int MAX_PACKAGE_CLASSES = 256;

    boolean eraseIfPresent(ServerLevel level, Entity selected, KillService service) {
        Resolution resolution = resolve(selected);
        if (resolution == null) {
            return false;
        }

        Object controller = resolution.controller();
        UltimatumMod.LOGGER.info(
                "Detected logical controller {} behind proxy {}; removing {} static registry reference(s)",
                controller.getClass().getName(), selected, resolution.registries().size());

        for (RegistryReference reference : resolution.registries()) {
            reference.remove(controller);
        }
        markControllerRemoved(controller);
        new GenericBossEventPurger(level).purgeObject(controller);

        Set<Entity> proxies = Collections.newSetFromMap(new IdentityHashMap<>());
        proxies.add(selected);
        collectControllerProxies(controller, selected, proxies);
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ServerPlayer) && directlyReferences(entity, controller)) {
                proxies.add(entity);
            }
        }
        for (Entity proxy : proxies) {
            if (!(proxy instanceof ServerPlayer)) {
                service.markAndErase(level, proxy, 400);
            }
        }

        UltimatumMod.LOGGER.info("Erased logical controller and {} proxy entity/entities",
                proxies.size());
        return true;
    }

    boolean eraseLookedAt(ServerLevel level, ServerPlayer player, double reach,
                          KillService service) {
        for (Entity candidate : LookTargetResolver.candidates(level, player, reach)) {
            if (eraseIfPresent(level, candidate, service)) {
                return true;
            }
        }
        return false;
    }

    private static Resolution resolve(Entity proxy) {
        for (Field field : instanceFields(proxy.getClass(), true)) {
            Object candidate = read(field, proxy);
            if (!isControllerCandidate(candidate, proxy)
                    || !directlyReferences(candidate, proxy)) {
                continue;
            }
            List<RegistryReference> registries = findStaticRegistries(candidate);
            if (!registries.isEmpty()) {
                return new Resolution(candidate, registries);
            }
        }
        return null;
    }

    private static boolean isControllerCandidate(Object candidate, Entity proxy) {
        if (candidate == null || candidate instanceof Entity || candidate instanceof Level
                || candidate instanceof Collection<?> || candidate instanceof Map<?, ?>
                || candidate.getClass().isArray() || isLeaf(candidate)) {
            return false;
        }
        String name = candidate.getClass().getName();
        if (name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("sun.") || name.startsWith("com.mojang.")
                || name.startsWith("net.minecraft.") || name.startsWith("net.minecraftforge.")) {
            return false;
        }
        return sameCodeSource(candidate.getClass(), proxy.getClass());
    }

    private static void collectControllerProxies(Object controller, Entity selected,
                                                  Set<Entity> result) {
        for (Field field : instanceFields(controller.getClass(), false)) {
            Object value = read(field, controller);
            if (value instanceof Entity entity) {
                if (entity == selected || directlyReferences(entity, controller)) {
                    result.add(entity);
                }
            } else if (value instanceof Collection<?> collection) {
                for (Object entry : snapshot(collection)) {
                    if (entry instanceof Entity entity
                            && (entity == selected || directlyReferences(entity, controller))) {
                        result.add(entity);
                    }
                }
            } else if (value != null && value.getClass().isArray()
                    && !value.getClass().getComponentType().isPrimitive()) {
                int length = Math.min(Array.getLength(value), 256);
                for (int index = 0; index < length; index++) {
                    Object entry = Array.get(value, index);
                    if (entry instanceof Entity entity
                            && (entity == selected || directlyReferences(entity, controller))) {
                        result.add(entity);
                    }
                }
            }
        }
    }

    private static boolean directlyReferences(Object owner, Object expected) {
        if (owner == null) {
            return false;
        }
        for (Field field : instanceFields(owner.getClass(), owner instanceof Entity)) {
            if (read(field, owner) == expected) {
                return true;
            }
        }
        return false;
    }

    private static List<RegistryReference> findStaticRegistries(Object controller) {
        List<RegistryReference> result = new ArrayList<>();
        for (Class<?> type : packageClasses(controller.getClass())) {
            for (Field field : declaredFields(type)) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object value = read(field, null);
                if (value instanceof Collection<?> collection) {
                    for (Object entry : snapshot(collection)) {
                        if (entry == controller) {
                            result.add(RegistryReference.collection(collection));
                        } else {
                            findManagerRegistries(entry, controller, result);
                        }
                    }
                } else if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : snapshot(map.entrySet())) {
                        if (entry.getKey() == controller || entry.getValue() == controller) {
                            result.add(RegistryReference.map(map, entry.getKey()));
                        } else {
                            findManagerRegistries(entry.getKey(), controller, result);
                            findManagerRegistries(entry.getValue(), controller, result);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Supports controller registries shaped as static map/list -> per-level manager ->
     * controller list. This is the same structural relationship used by handler-based
     * logical mobs, without depending on a Handler class or field name.
     */
    private static void findManagerRegistries(Object manager, Object controller,
                                              List<RegistryReference> result) {
        if (manager == null || manager == controller || manager instanceof Entity
                || manager instanceof Level || manager instanceof String
                || manager instanceof Number || manager instanceof Enum<?>
                || !sameCodeSource(manager.getClass(), controller.getClass())) {
            return;
        }
        for (Field field : instanceFields(manager.getClass(), false)) {
            Object nested = read(field, manager);
            if (nested instanceof Collection<?> collection) {
                for (Object entry : snapshot(collection)) {
                    if (entry == controller) {
                        RegistryReference reference = RegistryReference.collection(collection);
                        if (!result.contains(reference)) {
                            result.add(reference);
                        }
                        break;
                    }
                }
            } else if (nested instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : snapshot(map.entrySet())) {
                    if (entry.getKey() == controller || entry.getValue() == controller) {
                        RegistryReference reference = RegistryReference.map(map, entry.getKey());
                        if (!result.contains(reference)) {
                            result.add(reference);
                        }
                    }
                }
            }
        }
    }

    private static Set<Class<?>> packageClasses(Class<?> anchor) {
        Set<Class<?>> result = new LinkedHashSet<>();
        result.add(anchor);
        try {
            CodeSource source = anchor.getProtectionDomain().getCodeSource();
            if (source == null) {
                return result;
            }
            URL location = source.getLocation();
            URI uri = location.toURI();
            Path root = Path.of(uri);
            String packageName = anchor.getPackageName();
            String packagePath = packageName.replace('.', '/');
            List<String> classNames = new ArrayList<>();

            if (Files.isDirectory(root)) {
                Path directory = root.resolve(packagePath.replace('/', File.separatorChar));
                if (Files.isDirectory(directory)) {
                    try (var stream = Files.list(directory)) {
                        stream.filter(path -> path.getFileName().toString().endsWith(".class"))
                                .limit(MAX_PACKAGE_CLASSES)
                                .forEach(path -> classNames.add(packageName + "."
                                        + stripClassSuffix(path.getFileName().toString())));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(root.toFile())) {
                    String prefix = packagePath + "/";
                    var entries = jar.entries();
                    while (entries.hasMoreElements() && classNames.size() < MAX_PACKAGE_CLASSES) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!entry.isDirectory() && name.startsWith(prefix)
                                && name.endsWith(".class")
                                && name.indexOf('/', prefix.length()) < 0) {
                            classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
                        }
                    }
                }
            }

            ClassLoader loader = anchor.getClassLoader();
            for (String className : classNames) {
                try {
                    result.add(Class.forName(className, false, loader));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Could not scan controller package for {}",
                    anchor.getName(), error);
        }
        return result;
    }

    private static String stripClassSuffix(String name) {
        return name.substring(0, name.length() - 6);
    }

    private static void markControllerRemoved(Object controller) {
        for (Class<?> cursor = controller.getClass(); cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            for (Field field : declaredFields(cursor)) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class) {
                    continue;
                }
                String name = field.getName().toLowerCase(java.util.Locale.ROOT);
                if (Set.of("removed", "dead", "erased", "invalid", "disposed").contains(name)) {
                    writeBoolean(field, controller, true);
                } else if (Set.of("active", "alive", "valid").contains(name)) {
                    writeBoolean(field, controller, false);
                }
            }
        }
    }

    private static List<Field> instanceFields(Class<?> type, boolean stopAtMinecraftEntity) {
        List<Field> result = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            if (stopAtMinecraftEntity && cursor != type
                    && cursor.getName().startsWith("net.minecraft.")) {
                break;
            }
            for (Field field : declaredFields(cursor)) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    result.add(field);
                }
            }
        }
        return result;
    }

    private static Field[] declaredFields(Class<?> type) {
        try {
            return type.getDeclaredFields();
        } catch (Throwable ignored) {
            return new Field[0];
        }
    }

    private static Object read(Field field, Object owner) {
        try {
            boolean isStatic = Modifier.isStatic(field.getModifiers());
            Object base = isStatic ? ReflectionAccess.unsafe().staticFieldBase(field) : owner;
            long offset = isStatic
                    ? ReflectionAccess.unsafe().staticFieldOffset(field)
                    : ReflectionAccess.unsafe().objectFieldOffset(field);
            return ReflectionAccess.unsafe().getObjectVolatile(base, offset);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeBoolean(Field field, Object owner, boolean value) {
        try {
            ReflectionAccess.unsafe().putBooleanVolatile(owner,
                    ReflectionAccess.unsafe().objectFieldOffset(field), value);
        } catch (Throwable ignored) {
        }
    }

    private static boolean sameCodeSource(Class<?> first, Class<?> second) {
        try {
            CodeSource a = first.getProtectionDomain().getCodeSource();
            CodeSource b = second.getProtectionDomain().getCodeSource();
            return a != null && b != null && a.getLocation().equals(b.getLocation());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLeaf(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>;
    }

    private static <T> Collection<T> snapshot(Collection<T> source) {
        try {
            return new ArrayList<>(source);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private record Resolution(Object controller, List<RegistryReference> registries) {
    }

    private record RegistryReference(Object container, Object key, boolean map) {
        static RegistryReference collection(Collection<?> collection) {
            return new RegistryReference(collection, null, false);
        }

        static RegistryReference map(Map<?, ?> map, Object key) {
            return new RegistryReference(map, key, true);
        }

        @SuppressWarnings("unchecked")
        void remove(Object controller) {
            if (map && container instanceof Map<?, ?> rawMap) {
                ContainerBypass.removeMapKey((Map<Object, Object>) rawMap, key);
            } else if (container instanceof Collection<?> rawCollection) {
                ContainerBypass.removeCollection((Collection<Object>) rawCollection, controller);
            }
        }
    }
}
