package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode;

import org.objectweb.asm.AnnotationVisitor;

import java.util.List;

public class BytecodeArrayAnnotationVisitor extends AnnotationVisitor {
    private final List<Object> values;

    public BytecodeArrayAnnotationVisitor(int api, AnnotationVisitor annotationVisitor, List<Object> values) {
        super(api, annotationVisitor);
        this.values = values;
    }

    @Override
    public void visit(String name, Object value) {
        if (value instanceof String) {
            value = BytecodeUtils.getShortName((String) value);
        }
        values.add(value);
        super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
        desc = BytecodeUtils.getShortDesc(desc);
        values.add(desc + "." + value);
        super.visitEnum(name, desc, value);
    }
}
