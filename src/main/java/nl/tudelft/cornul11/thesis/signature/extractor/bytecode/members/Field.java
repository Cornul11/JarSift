package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class Field {
    public String name;
    public String desc;

    @Override
    public String toString() {
        return "Field{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                '}';
    }
}