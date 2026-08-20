package dev.srryo.parallaxaxiom.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;

/** Installs UniversalClassTransformer directly into ModuleClassLoader#getClassBytes. */
public final class LoaderTransformer implements ClassFileTransformer {
    private final AtomicBoolean installed = new AtomicBoolean();

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefining,
                            ProtectionDomain domain, byte[] bytes) {
        if (!"cpw/mods/cl/ModuleClassLoader".equals(className)
                || !installed.compareAndSet(false, true)) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            reader.accept(node, ClassReader.EXPAND_FRAMES);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!method.name.equals("getClassBytes")
                        || !method.desc.equals("(Ljava/lang/module/ModuleReader;"
                        + "Ljava/lang/module/ModuleReference;Ljava/lang/String;)[B")) {
                    continue;
                }
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != Opcodes.ARETURN) {
                        continue;
                    }
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    hook.add(new InsnNode(Opcodes.SWAP));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "dev/srryo/parallaxaxiom/agent/UniversalClassTransformer",
                            "transform", "(Ljava/lang/String;[B)[B", false));
                    method.instructions.insertBefore(instruction, hook);
                    changed = true;
                }
            }
            if (!changed) {
                installed.set(false);
                return null;
            }
            ClassWriter writer = new ClassWriter(reader,
                    ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            System.out.println("[Parallax Axiom Agent] ModuleClassLoader hook installed");
            return writer.toByteArray();
        } catch (Throwable error) {
            installed.set(false);
            System.err.println("[Parallax Axiom Agent] Loader transformation failed: " + error);
            return null;
        }
    }
}
