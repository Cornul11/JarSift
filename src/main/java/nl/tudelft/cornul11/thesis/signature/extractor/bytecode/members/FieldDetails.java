package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import java.util.ArrayList;
import java.util.List;

public class FieldDetails {
    public String name;
    public String desc;
    public List<AnnotationDetails> annotations = new ArrayList<>();

    @Override
    public String toString() {
        return "FieldDetails{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", annotations=" + annotations +
                '}';
    }
}