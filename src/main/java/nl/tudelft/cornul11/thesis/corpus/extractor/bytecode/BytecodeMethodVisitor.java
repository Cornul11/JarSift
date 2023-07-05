package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode;

import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.AnnotationDetails;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.InstructionDetails;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.MethodDetails;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;

public class BytecodeMethodVisitor extends MethodVisitor {
    private final MethodDetails method;

    public BytecodeMethodVisitor(int api, MethodVisitor methodVisitor, MethodDetails method) {
        super(api, methodVisitor);
        this.method = method;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        descriptor = BytecodeUtils.getShortDesc(descriptor);

        AnnotationDetails annotation = new AnnotationDetails(descriptor, visible);
        method.addAnnotation(annotation);

        return new BytecodeAnnotationVisitor(api, super.visitAnnotation(descriptor, visible), annotation);
    }

    @Override
    public void visitInsn(int opcode) {
        InstructionDetails instruction = new InstructionDetails(Integer.toString(opcode), null);
        method.addInstruction(instruction);
        super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        InstructionDetails instruction = new InstructionDetails(Integer.toString(opcode), Integer.toString(operand));
        method.addInstruction(instruction);
        super.visitIntInsn(opcode, operand);
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        InstructionDetails instruction = new InstructionDetails(Integer.toString(opcode), Integer.toString(var));
        method.addInstruction(instruction);
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        InstructionDetails instruction = new InstructionDetails(Integer.toString(opcode), BytecodeUtils.getShortName(type));
        method.addInstruction(instruction);
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }
}