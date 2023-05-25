package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class BytecodeEnum {
    public String name;
    public String outerName;
    public String innerName;
    public int access;

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + name.hashCode();
        result = 31 * result + outerName.hashCode();
        result = 31 * result + innerName.hashCode();
        result = 31 * result + access;
        return result;
    }
}