package dev.srryo.parallaxaxiom.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * MIT-derived architecture from NoSugar's LivingEntityTransformer, reduced to the
 * three return values required by Parallax Axiom's forced-death state.
 */
public final class UniversalClassTransformer {
    private static final String HOOK =
            "dev/srryo/parallaxaxiom/kill/hook/ExecutionMethodHooks";
    private static final String HOOK_INTERFACE =
            "dev/srryo/parallaxaxiom/kill/hook/ExecutionMethodHook";
    private static final String HOOK_DESCRIPTOR = "L" + HOOK_INTERFACE + ";";

    private UniversalClassTransformer() {
    }

    public static byte[] transform(String requestedName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return bytes;
        }
        try {
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode(Opcodes.ASM9);
            reader.accept(node, ClassReader.EXPAND_FRAMES);
            if (node.name.startsWith("dev/srryo/parallaxaxiom/agent/")
                    || node.name.startsWith("dev/srryo/parallaxaxiom/kill/hook/")
                    || node.name.startsWith("com/test/nosugar/")) {
                return bytes;
            }

            boolean changed = false;
            for (MethodNode method : node.methods) {
                HookSpec spec = HookSpec.forMethod(method.name, method.desc);
                if (spec == null || (method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    continue;
                }
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction.getOpcode() != spec.returnOpcode) {
                        continue;
                    }
                    InsnList hook = new InsnList();
                    hook.add(new FieldInsnNode(Opcodes.GETSTATIC, HOOK, "INSTANCE",
                            HOOK_DESCRIPTOR));
                    hook.add(new InsnNode(Opcodes.SWAP));
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, HOOK_INTERFACE,
                            spec.hookName, spec.hookDescriptor, true));
                    method.instructions.insertBefore(instruction, hook);
                    changed = true;
                }
            }
            if (!changed) {
                return bytes;
            }
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable error) {
            System.err.println("[Parallax Axiom Agent] Class transformation failed for "
                    + requestedName + ": " + error);
            return bytes;
        }
    }

    private record HookSpec(String hookName, String hookDescriptor, int returnOpcode) {
        private static HookSpec forMethod(String name, String descriptor) {
            if (descriptor.equals("()F")
                    && (name.equals("getHealth") || name.equals("m_21223_"))) {
                return new HookSpec("getHealth", "(FLjava/lang/Object;)F", Opcodes.FRETURN);
            }
            if (!descriptor.equals("()Z")) {
                return null;
            }
            if (name.equals("isAlive") || name.equals("m_6084_")) {
                return new HookSpec("isAlive", "(ZLjava/lang/Object;)Z", Opcodes.IRETURN);
            }
            if (name.equals("isDeadOrDying") || name.equals("m_21224_")) {
                return new HookSpec("isDeadOrDying", "(ZLjava/lang/Object;)Z",
                        Opcodes.IRETURN);
            }
            return null;
        }
    }
}
