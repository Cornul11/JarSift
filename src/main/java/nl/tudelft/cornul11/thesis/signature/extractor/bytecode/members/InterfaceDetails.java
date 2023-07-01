package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class InterfaceDetails {
    private String name;
    private String outerName;
    private String innerName;
    private int access;

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