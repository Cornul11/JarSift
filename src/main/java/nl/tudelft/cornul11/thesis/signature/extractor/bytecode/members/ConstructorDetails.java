package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class ConstructorDetails {
    private String name;
    private String desc;
    private String signature;
    private String[] exceptions;

    public ConstructorDetails(String name, String desc, String signature, String[] exceptions) {
        this.name = name;
        this.desc = desc;
        this.signature = signature;
        this.exceptions = exceptions;
    }

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