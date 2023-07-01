package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class InstructionDetails {
    private String opcode;
    private String operand;

    public InstructionDetails(String opcode, String operand) {
        this.opcode = opcode;
        this.operand = operand;
    }

    @Override
    public String toString() {
        return opcode + " " + operand;
    }
}