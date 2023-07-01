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

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(desc);
        sb.append(signature);
        if (exceptions != null) {
            for (String exception : exceptions) {
                sb.append(exception);
            }
        }
        return sb.toString();
    }

}