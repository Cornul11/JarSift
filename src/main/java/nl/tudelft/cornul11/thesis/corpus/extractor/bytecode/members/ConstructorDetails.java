package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members;

public class ConstructorDetails {
    private String name;
    private String desc;
    private String[] exceptions;

    public ConstructorDetails(String name, String desc, String[] exceptions) {
        this.name = name;
        this.desc = desc;
        this.exceptions = exceptions;
    }

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(desc);
        if (exceptions != null) {
            for (String exception : exceptions) {
                sb.append(exception);
            }
        }
        return sb.toString();
    }

}