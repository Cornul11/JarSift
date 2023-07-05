package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode;

import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.AnnotationDetails;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.FieldDetails;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;

public class BytecodeFieldVisitor extends FieldVisitor {
    private final FieldDetails field;

    public BytecodeFieldVisitor(int api, FieldVisitor fieldVisitor, FieldDetails field) {
        super(api, fieldVisitor);
        this.field = field;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        desc = BytecodeUtils.getShortDesc(desc);
        AnnotationDetails annotation = new AnnotationDetails(desc, visible);
        field.addAnnotation(annotation);
        return new BytecodeAnnotationVisitor(api, super.visitAnnotation(desc, visible), annotation);
    }
}
