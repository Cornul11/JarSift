package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members;

import java.util.ArrayList;
import java.util.List;

public class MethodDetails {
    private int access;
    private String name;
    private String desc;
    private String[] exceptions;
    private List<InstructionDetails> instructions = new ArrayList<>();
    private List<AnnotationDetails> annotations = new ArrayList<>();
    private List<String> argumentTypes = new ArrayList<>();
    private String returnType;

    public MethodDetails(int access, String name, String desc, String[] exceptions) {
        this.access = access;
        this.name = name;
        this.desc = desc;
        this.exceptions = exceptions;
    }

    public void addArgumentType(String argumentType) {
        argumentTypes.add(argumentType);
    }

    public void addAnnotation(AnnotationDetails annotation) {
        annotations.add(annotation);
    }

    public void addInstruction(InstructionDetails instruction) {
        instructions.add(instruction);
    }

    public void setReturnType(String className) {
        returnType = className;
    }

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(access);
        sb.append(name);
        sb.append(desc);
        if (exceptions != null) {
            for (String exception : exceptions) {
                sb.append(exception);
            }
        }
        instructions.forEach(instruction -> sb.append(instruction.toSignaturePart()));
        annotations.forEach(annotation -> sb.append(annotation.toSignaturePart()));
        argumentTypes.forEach(sb::append);
        sb.append(returnType);
        return sb.toString();
    }
}