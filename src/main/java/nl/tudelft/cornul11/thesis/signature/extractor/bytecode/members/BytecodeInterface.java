package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class BytecodeInterface {
    public String name;
    public String outerName;
    public String innerName;
    public int access;

    @Override
    public String toString() {
        return "BytecodeInterface{" +
                "name='" + name + '\'' +
                ", outerName='" + outerName + '\'' +
                ", innerName='" + innerName + '\'' +
                ", access=" + access +
                '}';
    }
}