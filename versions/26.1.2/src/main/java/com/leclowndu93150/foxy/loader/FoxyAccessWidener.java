package com.leclowndu93150.foxy.loader;

import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;
import java.util.stream.Collectors;

public class FoxyAccessWidener extends SimpleClassProcessor {
    private static final int VISIBILITY_MASK = Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_PUBLIC;

    private final AccessWidenerData data = AccessWidenerData.get();

    @Override
    public ProcessorName name() {
        return new ProcessorName("foxy", "access_widener");
    }

    @Override
    public Set<Target> targets() {
        return data.allTargets().stream().map(Target::new).collect(Collectors.toSet());
    }

    @Override
    public void transform(ClassNode node, SimpleTransformationContext context) {
        String className = node.name.replace('/', '.');

        if (data.classes.contains(className)) {
            node.access = toPublic(node.access);
            for (var inner : node.innerClasses) {
                if (inner.name.equals(node.name)) {
                    inner.access = toPublic(inner.access);
                }
            }
        }

        Set<String> wideFields = data.fields.get(className);
        if (wideFields != null) {
            for (FieldNode field : node.fields) {
                if (wideFields.contains(field.name)) {
                    field.access = toPublic(field.access);
                }
            }
        }

        Set<String> wideMethods = data.methods.get(className);
        if (wideMethods != null) {
            for (MethodNode method : node.methods) {
                if (wideMethods.contains(method.name + method.desc)) {
                    method.access = toPublic(method.access);
                }
            }
        }
    }

    private static int toPublic(int access) {
        return (access & ~VISIBILITY_MASK) | Opcodes.ACC_PUBLIC;
    }
}
