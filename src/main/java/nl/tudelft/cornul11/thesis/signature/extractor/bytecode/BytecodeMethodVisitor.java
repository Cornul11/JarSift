package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.InstructionDetails;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.MethodDetails;
import org.objectweb.asm.MethodVisitor;
public class BytecodeMethodVisitor extends MethodVisitor {
    private final MethodDetails method;

    public BytecodeMethodVisitor(int api, MethodVisitor methodVisitor, MethodDetails method) {
        super(api, methodVisitor);
        this.method = method;
    }

    @Override
    public void visitInsn(int opcode) {
        InstructionDetails instruction = new InstructionDetails();
        instruction.opcode = Integer.toString(opcode);
        method.instructions.add(instruction);
        super.visitInsn(opcode);
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        InstructionDetails instruction = new InstructionDetails();
        instruction.opcode = Integer.toString(opcode);
        instruction.operand = Integer.toString(operand);
        method.instructions.add(instruction);
        super.visitIntInsn(opcode, operand);
    }

    // TODO: maybe add more visit*Insn methods if needed

    @Override
    public void visitVarInsn(int opcode, int var) {
        InstructionDetails instruction = new InstructionDetails();
        instruction.opcode = Integer.toString(opcode);
        instruction.operand = Integer.toString(var);
        method.instructions.add(instruction);
        super.visitVarInsn(opcode, var);
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        InstructionDetails instruction = new InstructionDetails();
        instruction.opcode = Integer.toString(opcode);
        instruction.operand = type;
        method.instructions.add(instruction);
        super.visitTypeInsn(opcode, type);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
    }
}