package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.AnnotationDetails;
import org.objectweb.asm.AnnotationVisitor;

import java.util.ArrayList;
import java.util.List;

public class BytecodeAnnotationVisitor extends AnnotationVisitor {
    private final AnnotationDetails annotation;

    public BytecodeAnnotationVisitor(int api, AnnotationVisitor annotationVisitor, AnnotationDetails annotation) {
        super(api, annotationVisitor);
        this.annotation = annotation;
    }

    @Override
    public void visit(String name, Object value) {
        if (value instanceof String) {
            value = BytecodeUtils.getShortName((String) value);
        }
        annotation.arguments.put(name, value);
        super.visit(name, value);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        name = BytecodeUtils.getShortName(name);
        List<Object> values = new ArrayList<>();
        annotation.arrayArguments.put(name, values);
        return new BytecodeArrayAnnotationVisitor(api, super.visitArray(name), values);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
        AnnotationDetails nestedAnnotation = new AnnotationDetails();
        nestedAnnotation.desc = BytecodeUtils.getShortDesc(desc);
        annotation.annotationArguments.put(name, nestedAnnotation);
        return new BytecodeAnnotationVisitor(api, super.visitAnnotation(name, desc), nestedAnnotation);
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
        desc = BytecodeUtils.getShortDesc(desc);
        value = BytecodeUtils.getShortName(value);
        annotation.arguments.put(name, desc + "." + value);
        super.visitEnum(name, desc, value);
    }
}