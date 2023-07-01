package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class NestedClassDetails {
    public String name;
    public String outerName;
    public String innerName;
    public int access;
    public String type;

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
