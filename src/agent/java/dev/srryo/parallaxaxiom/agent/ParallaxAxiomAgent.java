package dev.srryo.parallaxaxiom.agent;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public final class ParallaxAxiomAgent {
    private ParallaxAxiomAgent() {
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        try {
            String path = System.getProperty("parallax_axiom.agent.jar");
            if (path != null && new File(path).isFile()) {
                JarFile jar = new JarFile(path);
                instrumentation.appendToBootstrapClassLoaderSearch(jar);
                instrumentation.appendToSystemClassLoaderSearch(jar);
            }

            LoaderTransformer transformer = new LoaderTransformer();
            instrumentation.addTransformer(transformer, true);
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (type.getName().equals("cpw.mods.cl.ModuleClassLoader")
                        && instrumentation.isModifiableClass(type)) {
                    instrumentation.retransformClasses(type);
                    break;
                }
            }
        } catch (Throwable error) {
            System.err.println("[Parallax Axiom Agent] Installation failed: " + error);
            error.printStackTrace(System.err);
        }
    }
}
