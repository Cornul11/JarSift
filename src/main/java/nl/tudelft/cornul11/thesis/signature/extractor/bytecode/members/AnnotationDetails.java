package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationDetails {
    private String desc;
    private Boolean visible;
    private Map<String, Object> arguments = new HashMap<>();
    private Map<String, List<Object>> arrayArguments = new HashMap<>();
    private Map<String, AnnotationDetails> annotationArguments = new HashMap<>();

    public AnnotationDetails(String desc, Boolean visible) {
        this.desc = desc;
        this.visible = visible;
    }

    public void putAnnotationArgument(String name, AnnotationDetails nestedAnnotation) {
        annotationArguments.put(name, nestedAnnotation);
    }

    public void putArgument(String name, Object s) {
        arguments.put(name, s);
    }

    public void putArrayArgument(String name, List<Object> values) {
        arrayArguments.put(name, values);
    }

    public String toSignaturePart() {
        StringBuilder sb = new StringBuilder();
        sb.append(desc);
        sb.append(visible);
        arguments.values().forEach(sb::append);
        arrayArguments.values().stream().flatMap(List::stream).forEach(sb::append);
        annotationArguments.values().forEach(annotation -> sb.append(annotation.toSignaturePart()));
        return sb.toString();
    }
}