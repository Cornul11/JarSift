package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodDetails {
    public String name;
    public String desc;
    public String signature;
    public String[] exceptions;
    public List<InstructionDetails> instructions = new ArrayList<>();

    @Override
    public String toString() {
        return "MethodDetails{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", signature='" + signature + '\'' +
                ", exceptions=" + Arrays.toString(exceptions) +
                ", instructions=" + instructions +
                '}';
    }
}