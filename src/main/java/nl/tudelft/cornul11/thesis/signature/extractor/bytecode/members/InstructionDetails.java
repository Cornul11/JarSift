package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class InstructionDetails {
    public String opcode;
    public String operand;

    @Override
    public String toString() {
        return opcode + " " + operand;
    }
}