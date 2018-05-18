package org.aion.avm.core.stacktracking;

import org.aion.avm.internal.StackWatcher;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.commons.*;

import java.util.ArrayList;

public class StackWatcherClassAdapter extends ClassVisitor implements Opcodes{
    public StackWatcherClassAdapter(final ClassVisitor cv) {
        super(Opcodes.ASM6, cv);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name,
            final String desc, final String signature, final String[] exceptions)
    {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        GeneratorAdapter ga = new GeneratorAdapter(mv, access, name, desc);
        StackWatcherMethodAdapter ma = new StackWatcherMethodAdapter(ga, access, name, desc);

        // Wrap the method adapter into a method node to access method informaton
        return new MethodNode(Opcodes.ASM6, access, name, desc, signature, exceptions)
        {
            @Override
            public void visitEnd() {
                ma.setTryCatchBlockNum(this.tryCatchBlocks.size());
                ma.setMax(this.maxLocals, this.maxStack);
                this.accept(ma);
            }
        };
    }
}

