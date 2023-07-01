package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class InstructionDetails {
    private String opcode;
    private String operand;

    public InstructionDetails(String opcode, String operand) {
        this.opcode = opcode;
        this.operand = operand;
    }

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(opcode);
        sb.append(operand);
        return sb.toString();
    }
}