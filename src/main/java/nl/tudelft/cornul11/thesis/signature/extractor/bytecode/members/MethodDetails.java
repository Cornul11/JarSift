package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MethodDetails {
    private int access;
    private String name;
    private String desc;
    private String signature;
    private String[] exceptions;
    private List<InstructionDetails> instructions = new ArrayList<>();
    private List<AnnotationDetails> annotations = new ArrayList<>();
    private List<String> argumentTypes = new ArrayList<>();

    public MethodDetails(int access, String name, String desc, String signature, String[] exceptions) {
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions;
        this.inferArgumentTypes(desc);
    }

    public void inferArgumentTypes(String desc) {
        this.desc = desc;
        Type methodType = Type.getMethodType(desc);
        for (Type argumentType : methodType.getArgumentTypes()) {
            argumentTypes.add(argumentType.getClassName());
        }
    }

    public void addAnnotation(AnnotationDetails annotation) {
        annotations.add(annotation);
    }

    public void addInstruction(InstructionDetails instruction) {
        instructions.add(instruction);
    }

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(access);
        sb.append(name);
        sb.append(desc);
        sb.append(signature);
        if (exceptions != null) {
            for (String exception : exceptions) {
                sb.append(exception);
            }
        }
        instructions.forEach(instruction -> sb.append(instruction.toSignaturePart()));
        annotations.forEach(annotation -> sb.append(annotation.toSignaturePart()));
        argumentTypes.forEach(sb::append);
        return sb.toString();
    }
}