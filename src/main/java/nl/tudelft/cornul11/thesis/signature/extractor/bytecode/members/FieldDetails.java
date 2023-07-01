package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import java.util.ArrayList;
import java.util.List;

public class FieldDetails {
    private String name;
    private String desc;
    private List<AnnotationDetails> annotations = new ArrayList<>();

    public FieldDetails(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    @Override
    public String toString() {
        return "FieldDetails{" +
                "name='" + name + '\'' +
                ", desc='" + desc + '\'' +
                ", annotations=" + annotations +
                '}';
    }

    public void addAnnotation(AnnotationDetails annotation) {
        annotations.add(annotation);
    }
}