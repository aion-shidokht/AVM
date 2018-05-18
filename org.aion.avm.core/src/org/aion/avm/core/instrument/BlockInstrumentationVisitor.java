package org.aion.avm.core.instrument;

import java.util.List;

import org.aion.avm.core.util.Assert;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


/**
 * A visitor responsible for re-writing the methods with the various call-outs and other manipulations.
 * 
 * Prepending instrumentation is one of the more complex ASM interactions, so it warrants some explanation:
 * -we will advance through the block list we were given while walking the blocks, much like BlockMethodReader.
 * -when we reach the beginning of a new block, we will inject the energy accounting helper before passing the
 * method through to the writer.
 * 
 * Array allocation replacement is also one the more complex cases, worth explaining:
 * -newarray - call to special static helpers, based on underlying native:  no change to stack shape
 * -anewarray - call to special static helper, requires pushing the associated class constant onto the stack
 * -multianewarray - call to special static helpers, requires pushing the associated class constant onto the stack
 * Only anewarray is done without argument introspection.  Note that multianewarray can be called for any [2..255]
 * dimension array.
 * TODO: Generate the source for these helpers.  For now, we will just define that [2..4] are allowed.
 * 
 * Note that this was adapted from the ClassRewriter.MethodInstrumentationVisitor.
 */
public class BlockInstrumentationVisitor extends MethodVisitor {
    private final String runtimeClassName;
    private final MethodVisitor target;
    private final List<BasicBlock> blocks;
    private boolean scanningToNewBlockStart;
    private int nextBlockIndexToWrite;

    public BlockInstrumentationVisitor(String runtimeClassName, MethodVisitor target, List<BasicBlock> blocks) {
        super(Opcodes.ASM6, null);
        this.runtimeClassName = runtimeClassName;
        this.target = target;
        this.blocks = blocks;
    }

    @Override
    public void visitCode() {
        // We initialize the state machine.
        this.scanningToNewBlockStart = true;
        this.nextBlockIndexToWrite = 0;
        // We also need to tell the writer to advance.
        this.target.visitCode();
    }
    @Override
    public void visitEnd() {
        // We never have empty blocks, in our implementation, so we should always be done when we reach this point.
        Assert.assertTrue(this.blocks.size() == this.nextBlockIndexToWrite);
        // Tell the writer we are done.
        this.target.visitEnd();
    }
    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        checkInject();
        this.target.visitFieldInsn(opcode, owner, name, descriptor);
    }
    @Override
    public void visitIincInsn(int var, int increment) {
        checkInject();
        this.target.visitIincInsn(var, increment);
    }
    @Override
    public void visitInsn(int opcode) {
        checkInject();
        this.target.visitInsn(opcode);
    }
    @Override
    public void visitIntInsn(int opcode, int operand) {
        checkInject();
        // This is where the newarray bytecode might appear:  the operand is a specially-defined list of primitive types.
        if (Opcodes.NEWARRAY == opcode) {
            // We will handle this through the common multianewarray1 helper instead of creating a new helper for every case.
            String type = null;
            String descriptor = null;
            switch (operand) {
            case 4: {
                // boolean
                type = "java/lang/Boolean";
                descriptor = "[Z";
                break;
            }
            case 5: {
                // char
                type = "java/lang/Character";
                descriptor = "[C";
                break;
            }
            case 6: {
                // float
                type = "java/lang/Float";
                descriptor = "[F";
                break;
            }
            case 7: {
                // double
                type = "java/lang/Double";
                descriptor = "[D";
                break;
            }
            case 8: {
                // byte
                type = "java/lang/Byte";
                descriptor = "[B";
                break;
            }
            case 9: {
                // short
                type = "java/lang/Short";
                descriptor = "[S";
                break;
            }
            case 10: {
                // int
                type = "java/lang/Integer";
                descriptor = "[I";
                break;
            }
            case 11: {
                // long
                type = "java/lang/Long";
                descriptor = "[J";
                break;
            }
            default:
                Assert.unreachable("Unknown newarray operand: " + operand);
            }
            this.target.visitFieldInsn(Opcodes.GETSTATIC, type, "TYPE", "Ljava/lang/Class;");
            String methodName = "multianewarray1";
            String methodDescriptor = "(ILjava/lang/Class;)Ljava/lang/Object;";
            this.target.visitMethodInsn(Opcodes.INVOKESTATIC, this.runtimeClassName, methodName, methodDescriptor, false);
            this.target.visitTypeInsn(Opcodes.CHECKCAST, descriptor);
        } else {
            this.target.visitIntInsn(opcode, operand);
        }
    }
    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        Assert.unreachable("invokedynamic must be filtered prior to updating basic blocks");
    }
    @Override
    public void visitJumpInsn(int opcode, Label label) {
        checkInject();
        this.target.visitJumpInsn(opcode, label);
    }
    @Override
    public void visitLabel(Label label) {
        // The label means that we found a new block (although there might be several labels before it actually starts)
        // so enter the state machine mode where we are looking for that beginning of a block.
        this.scanningToNewBlockStart = true;
        this.target.visitLabel(label);
    }
    @Override
    public void visitLdcInsn(Object value) {
        checkInject();
        this.target.visitLdcInsn(value);
    }
    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        checkInject();
        this.target.visitLookupSwitchInsn(dflt, keys, labels);
    }
    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        checkInject();
        this.target.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }
    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        checkInject();
        // We don't actually want to write multianewarray bytecodes, but always replace them with call-outs.
        if (numDimensions > 3) {
            // TODO:  Build something to generate the rest of the helpers we may need, here.  We will only go to 3 for the initial tests.
            Assert.unimplemented("TODO:  Build something to generate the rest of the helpers we may need, here.  We will only go to 3 for the initial tests.");
        }
        // TODO:  Can we be certain numDimensions is at least 2 (if it can't be one, we have an unused method in the static)?
        // This is just like anewarray, except that the invokestatic target differs, based on numDimensions.
        
        // NOTE:  This bytecode is also used for primitive multi-arrays which DO NOT have "L" or ";" in their descriptors.
        // For example, "long[][]" is "[[J" so we need to handle those a little differently.
        // Also note that primitive array construction is done very oddly:  since primitives don't have classes, a placeholder is loaded from
        // the corresponding capital arraywrapper class, as a static.  We need to generate those special-cases here.
        
        int indexOfL = descriptor.indexOf("L");
        boolean isObjectType = (-1 != indexOfL);
        // Note that we load class references via ldc but primitive types via getstatic.
        if (isObjectType) {
            // The descriptor we are given here is the arrayclass descriptor, along the lines of "[[[Ljava/lang/String;" but our helper
            // wants to receive the raw class so convert it, here, by pruning "[*L" and ";" from the descriptor.
            String prunedClassName = descriptor.substring(indexOfL + 1, descriptor.length() - 1);
            this.target.visitLdcInsn(Type.getObjectType(prunedClassName));
        } else {
            // java/lang/Long.TYPE:Ljava/lang/Class;
            String typeName = descriptor.replaceAll("\\[", "");
            // We expect that this is only 1 char (or we parsed this wrong or were passed something we couldn't interpret).
            Assert.assertTrue(1 == typeName.length());
            String type = null;
            switch (typeName.charAt(0)) {
            case 'Z': {
                type = "java/lang/Boolean";
                break;
            }
            case 'B': {
                type = "java/lang/Byte";
                break;
            }
            case 'C': {
                type = "java/lang/Char";
                break;
            }
            case 'S': {
                type = "java/lang/Short";
                break;
            }
            case 'I': {
                type = "java/lang/Integer";
                break;
            }
            case 'J': {
                type = "java/lang/Long";
                break;
            }
            case 'F': {
                type = "java/lang/Float";
                break;
            }
            case 'D': {
                type = "java/lang/Double";
                break;
            }
            default:
                Assert.unreachable("Unknown primitive type: \"" + typeName + "\"");
            }
            this.target.visitFieldInsn(Opcodes.GETSTATIC, type, "TYPE", "Ljava/lang/Class;");
        }
        String argList = "(";
        for (int i = 0; i < numDimensions; ++i) {
            argList += "I";
        }
        argList += "Ljava/lang/Class;)";
        String methodName = "multianewarray" + numDimensions;
        String methodDescriptor = argList + "Ljava/lang/Object;";
        this.target.visitMethodInsn(Opcodes.INVOKESTATIC, this.runtimeClassName, methodName, methodDescriptor, false);
        this.target.visitTypeInsn(Opcodes.CHECKCAST, descriptor);
    }
    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        checkInject();
        this.target.visitTableSwitchInsn(min, max, dflt, labels);
    }
    @Override
    public void visitTypeInsn(int opcode, String type) {
        checkInject();
        // This is where we might see anewarray, so see if we need to replace it with a helper.
        if (Opcodes.ANEWARRAY == opcode) {
            // Inject our special idiom:  ldc then invokestatic, finally checkcast.
            this.target.visitLdcInsn(Type.getObjectType(type));
            // We just use the common multianewarray1 helper, since it can cover the common 1-dimensional cases for both objects and primitives.
            this.target.visitMethodInsn(Opcodes.INVOKESTATIC, this.runtimeClassName, "multianewarray1", "(ILjava/lang/Class;)Ljava/lang/Object;", false);
            this.target.visitTypeInsn(Opcodes.CHECKCAST, "[L" + type + ";");
        } else {
            this.target.visitTypeInsn(opcode, type);
        }
    }
    @Override
    public void visitVarInsn(int opcode, int var) {
        checkInject();
        this.target.visitVarInsn(opcode, var);
    }
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        this.target.visitMaxs(maxStack, maxLocals);
    }
    /**
     * Common state machine advancing call.  Called at every instruction to see if we need to inject and/or advance
     * the state machine.
     */
    private void checkInject() {
        if (this.scanningToNewBlockStart) {
            // We were witing for this so see if we have to do anything.
            BasicBlock currentBlock = this.blocks.get(this.nextBlockIndexToWrite);
            if (currentBlock.getEnergyCost() > 0) {
                // Inject the bytecodes.
                this.target.visitLdcInsn(Long.valueOf(currentBlock.getEnergyCost()));
                this.target.visitMethodInsn(Opcodes.INVOKESTATIC, this.runtimeClassName, "chargeEnergy", "(J)V", false);
            }
            // Reset the state machine for the next block.
            this.scanningToNewBlockStart = false;
            this.nextBlockIndexToWrite += 1;
        }
    }
}
