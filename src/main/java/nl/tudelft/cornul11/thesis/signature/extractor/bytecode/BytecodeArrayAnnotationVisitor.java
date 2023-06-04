package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import org.objectweb.asm.AnnotationVisitor;

import java.util.List;

public class BytecodeArrayAnnotationVisitor extends AnnotationVisitor {
    private List<Object> values;

    public BytecodeArrayAnnotationVisitor(int api, AnnotationVisitor annotationVisitor, List<Object> values) {
        super(api, annotationVisitor);
        this.values = values;
    }

    @Override
    public void visit(String name, Object value) {
        values.add(value);
        super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
        values.add(desc + "." + value);
        super.visitEnum(name, desc, value);
    }
}
