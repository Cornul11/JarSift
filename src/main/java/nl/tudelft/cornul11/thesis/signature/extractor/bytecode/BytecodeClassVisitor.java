package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;
import org.objectweb.asm.*;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.ASM9;

public class BytecodeClassVisitor extends ClassVisitor {
    private final BytecodeDetails bytecodeDetails = new BytecodeDetails();

    public BytecodeClassVisitor() {
        super(ASM9);
    }

    public BytecodeDetails getBytecodeClass() {
        return bytecodeDetails;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        bytecodeDetails.name = BytecodeUtils.getShortName(name);
        bytecodeDetails.extendsType = BytecodeUtils.getShortName(superName);
        bytecodeDetails.interfaces = Arrays.stream(interfaces).map(BytecodeUtils::getShortName).collect(Collectors.toList());
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        desc = BytecodeUtils.getShortDesc(desc);

        AnnotationDetails annotationDetails = new AnnotationDetails();
        annotationDetails.desc = desc;
        annotationDetails.visible = visible;
        bytecodeDetails.annotations.add(annotationDetails);

        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        return new BytecodeAnnotationVisitor(ASM9, av, annotationDetails);
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        name = BytecodeUtils.getShortName(name);
        if (outerName != null)
            outerName = BytecodeUtils.getShortName(outerName);

        NestedClassDetails nestedClassDetails = new NestedClassDetails();
        nestedClassDetails.name = name;
        nestedClassDetails.outerName = outerName;
        nestedClassDetails.innerName = innerName;
        nestedClassDetails.access = access;

        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            nestedClassDetails.type = "INTERFACE";
        } else if ((access & Opcodes.ACC_ENUM) != 0) {
            nestedClassDetails.type = "ENUM";
        } else if ((access & Opcodes.ACC_STATIC) != 0) {
            nestedClassDetails.type = "STATIC_CLASS";
        } else {
            nestedClassDetails.type = "INNER_CLASS";
        }

        bytecodeDetails.innerClasses.add(nestedClassDetails);
    }

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        desc = BytecodeUtils.getShortDesc(desc);

        FieldDetails fieldDetails = new FieldDetails();
        fieldDetails.name = name;
        fieldDetails.desc = desc;
        bytecodeDetails.fields.add(fieldDetails);

        FieldVisitor originalVisitor = super.visitField(access, name, desc, signature, value);
        return new BytecodeFieldVisitor(api, originalVisitor, fieldDetails);
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        desc = BytecodeUtils.getShortDesc(desc);
        signature = signature != null ? BytecodeUtils.getShortDesc(signature) : null;

        MethodDetails method = new MethodDetails();
        method.access = access;
        method.name = name;
        method.setDesc(desc);
        method.signature = signature;
        method.exceptions = exceptions != null ? Arrays.stream(exceptions).map(BytecodeUtils::getShortName).toArray(String[]::new) : null;

        if ("<init>".equals(name)) {
            // This is a constructor
            ConstructorDetails constructor = new ConstructorDetails();
            constructor.name = name;
            constructor.desc = desc;
            constructor.signature = signature;
            constructor.exceptions = exceptions != null ? Arrays.stream(exceptions).map(BytecodeUtils::getShortName).toArray(String[]::new) : null;
            bytecodeDetails.constructors.add(constructor);
        } else {
            // This is a method
            bytecodeDetails.methods.add(method);
        }

        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new BytecodeMethodVisitor(ASM9, mv, method);
    }
}