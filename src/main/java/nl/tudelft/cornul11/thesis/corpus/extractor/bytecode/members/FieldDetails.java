package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members;

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

    public void addAnnotation(AnnotationDetails annotation) {
        annotations.add(annotation);
    }

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(desc);
        annotations.forEach(annotation -> sb.append(annotation.toSignaturePart()));
        return sb.toString();
    }
}