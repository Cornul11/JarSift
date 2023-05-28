package nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members;

public class AnnotationDetails {
    public String desc;
    public boolean visible;

    @Override
    public String toString() {
        return "BytecodeAnnotation{" +
                "desc='" + desc + '\'' +
                ", visible=" + visible +
                '}';
    }
}
