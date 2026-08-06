package dev.srryo.ultimatum.agentloader;

import com.sun.tools.attach.VirtualMachine;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;

/** Extracts and self-attaches the embedded universal method-hook agent. */
public final class AgentLoader {
    private static volatile boolean loaded;

    private AgentLoader() {
    }

    public static synchronized boolean loadUnlessNoSugarAgentPresent() {
        if (loaded) {
            return true;
        }
        try {
            Class.forName("com.test.nosugar.agent.transformer.TransformerCore", false,
                    ClassLoader.getSystemClassLoader());
            System.out.println("[Ultimatum] NoSugar agent detected; sharing its method hooks");
            loaded = true;
            return true;
        } catch (Throwable ignored) {
        }

        try {
            allowAttachSelf();
            File agent = extractAgent();
            if (agent == null) {
                System.err.println("[Ultimatum] Embedded agent resource was not found");
                return false;
            }
            agent.deleteOnExit();
            System.setProperty("ultimatum.agent.jar", agent.getAbsolutePath());

            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            VirtualMachine machine = VirtualMachine.attach(pid);
            try {
                machine.loadAgent(agent.getAbsolutePath());
            } finally {
                machine.detach();
            }
            loaded = true;
            System.out.println("[Ultimatum] Universal method-hook agent loaded");
            return true;
        } catch (Throwable error) {
            System.err.println("[Ultimatum] Could not load universal method-hook agent: "
                    + error);
            return false;
        }
    }

    private static File extractAgent() throws Exception {
        try (InputStream input = AgentLoader.class.getResourceAsStream(
                "/META-INF/jarjar/ultimatum-agent.jar")) {
            if (input == null) {
                return null;
            }
            File output = File.createTempFile("ultimatum-agent", ".jar");
            try (FileOutputStream stream = new FileOutputStream(output)) {
                input.transferTo(stream);
            }
            return output;
        }
    }

    private static void allowAttachSelf() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Class<?> virtualMachine = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
        for (Field field : virtualMachine.getDeclaredFields()) {
            if (field.getType() == boolean.class
                    && field.getName().toLowerCase(java.util.Locale.ROOT)
                    .contains("allow_attach_self")) {
                unsafe.putBoolean(unsafe.staticFieldBase(field),
                        unsafe.staticFieldOffset(field), true);
                return;
            }
        }
    }
}
