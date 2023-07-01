package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import org.objectweb.asm.Opcodes;

public class NestedClassDetails {
    private String name;
    private String outerName;
    private String innerName;
    private int access;
    private String type;

    public NestedClassDetails(String name, String outerName, String innerName, int access) {
        this.name = name;
        this.outerName = outerName;
        this.innerName = innerName;
        this.access = access;
        this.type = inferType(access);
    }

    private String inferType(int access) {
        String type = "INNER_CLASS";
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            type = "INTERFACE";
        } else if ((access & Opcodes.ACC_ENUM) != 0) {
            type = "ENUM";
        } else if ((access & Opcodes.ACC_STATIC) != 0) {
            type = "STATIC_CLASS";
        }
        return type;
    }

    @Override
    public String toString() {
        return "BytecodeNestedClass{" +
                "name='" + name + '\'' +
                ", outerName='" + outerName + '\'' +
                ", innerName='" + innerName + '\'' +
                ", access=" + access +
                ", type='" + type + '\'' +
                '}';
    }
}
