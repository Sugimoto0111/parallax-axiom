package dev.srryo.parallaxaxiom.kill;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public final class ContainerBypass {
    private static final MethodHandles.Lookup TRUSTED_LOOKUP = trustedLookup();

    private ContainerBypass() {
    }

    public static boolean removeCollection(Collection<?> collection, Object value) {
        try {
            if (invokeSuperclass(collection, "remove", new Class<?>[]{Object.class}, value)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return collection.remove(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean removeMapKey(Map<?, ?> map, Object key) {
        try {
            if (invokeSuperclass(map, "remove", new Class<?>[]{Object.class}, key)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        if (key instanceof Integer integer) {
            try {
                if (invokeSuperclass(map, "remove", new Class<?>[]{int.class}, integer.intValue())) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            int before = map.size();
            map.remove(key);
            return map.size() != before;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean putMapEntry(Map<?, ?> map, Object key, Object value) {
        try {
            if (invokeSuperclass(map, "put", new Class<?>[]{Object.class, Object.class}, key, value)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            ((Map) map).put(key, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean invokeSuperclass(Object receiver, String name, Class<?>[] parameters,
                                           Object... arguments) throws Throwable {
        Class<?> runtime = receiver.getClass();
        for (Class<?> cursor = runtime.getSuperclass(); cursor != null; cursor = cursor.getSuperclass()) {
            Method method;
            try {
                method = cursor.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            MethodType type = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            Object result = TRUSTED_LOOKUP.findSpecial(cursor, name, type, runtime)
                    .bindTo(receiver).invokeWithArguments(arguments);
            if (method.getReturnType() == boolean.class) {
                return Boolean.TRUE.equals(result);
            }
            return true;
        }
        return false;
    }

    private static MethodHandles.Lookup trustedLookup() {
        try {
            Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object base = ReflectionAccess.unsafe().staticFieldBase(field);
            long offset = ReflectionAccess.unsafe().staticFieldOffset(field);
            return (MethodHandles.Lookup) ReflectionAccess.unsafe().getObject(base, offset);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
