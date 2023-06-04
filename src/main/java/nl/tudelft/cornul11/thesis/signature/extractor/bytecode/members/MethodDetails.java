package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodDetails {
    public String name;
    public String desc;
    public String signature;
    public String[] exceptions;
    public List<InstructionDetails> instructions = new ArrayList<>();
    public List<String> argumentTypes = new ArrayList<>();

    public void setDesc(String desc) {
        this.desc = desc;
        Type methodType = Type.getMethodType(desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            argumentTypes.add(argumentType.getClassName());
        }
    }

    @Override
    public String toString() {
        return "MethodDetails{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", signature='" + signature + '\'' +
                ", exceptions=" + Arrays.toString(exceptions) +
                ", instructions=" + instructions +
                ", argumentTypes=" + argumentTypes +
                '}';
    }
}