package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class JavaConstructor {
    public String name;
    public String desc;
    public String signature;
    public String[] exceptions;

    @Override
    public String toString() {
        return "JavaConstructor{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", signature='" + signature + '\'' +
                ", exceptions=" + java.util.Arrays.toString(exceptions) +
                '}';
    }
}