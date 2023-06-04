package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnnotationDetails {
    public String desc;
    public boolean visible;
    public Map<String, Object> arguments = new HashMap<>();
    public Map<String, List<Object>> arrayArguments = new HashMap<>();
    public Map<String, AnnotationDetails> annotationArguments = new HashMap<>();

    @Override
    public String toString() {
        return "AnnotationDetails{" +
                "desc='" + desc + '\'' +
                ", visible=" + visible +
                ", arguments=" + arguments +
                ", arrayArguments=" + arrayArguments +
                ", annotationArguments=" + annotationArguments +
                '}';
    }
}