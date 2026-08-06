package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Loads the tiny Pig2 erasure kernel as a hidden class. ModLauncher launch plugins only
 * transform named classes loaded through its TransformingClassLoader; hidden definitions
 * do not pass through that path.
 */
public final class TrustedKernel {
    private static final MethodHandle EXECUTE_PIG;
    private static final MethodHandle RESET_PIG;

    static {
        MethodHandle execute = null;
        MethodHandle reset = null;
        try {
            byte[] bytes = readTemplate();
            MethodHandles.Lookup hiddenLookup = MethodHandles.lookup()
                    .defineHiddenClass(bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
            Class<?> kernel = hiddenLookup.lookupClass();
            execute = hiddenLookup.findStatic(kernel, "executePig",
                    MethodType.methodType(boolean.class, Object.class, Object.class));
            reset = hiddenLookup.findStatic(kernel, "resetPig",
                    MethodType.methodType(void.class, Object.class));
        } catch (Throwable error) {
            UltimatumMod.LOGGER.error("Could not initialize the hidden execution kernel", error);
        }
        EXECUTE_PIG = execute;
        RESET_PIG = reset;
    }

    private TrustedKernel() {
    }

    public static boolean executePig(Object target, Object level) {
        if (EXECUTE_PIG == null) {
            return false;
        }
        try {
            return (boolean) EXECUTE_PIG.invokeExact(target, level);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.error("Hidden Pig2 execution failed", error);
            return false;
        }
    }

    public static void resetPig(Object pig2Class) {
        if (RESET_PIG == null) {
            return;
        }
        try {
            RESET_PIG.invokeExact(pig2Class);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.error("Hidden Pig2 reset failed", error);
        }
    }

    private static byte[] readTemplate() throws IOException {
        try (InputStream input = TrustedKernel.class.getResourceAsStream("TrustedKernelTemplate.class")) {
            if (input == null) {
                throw new IOException("TrustedKernelTemplate.class is missing");
            }
            return input.readAllBytes();
        }
    }
}
