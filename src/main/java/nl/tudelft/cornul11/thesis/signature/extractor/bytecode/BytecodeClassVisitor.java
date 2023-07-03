package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;
import org.objectweb.asm.*;

import java.util.Arrays;

import static org.objectweb.asm.Opcodes.ASM9;

public class BytecodeClassVisitor extends ClassVisitor {
    private final BytecodeDetails.Builder bytecodeDetailsBuilder = new BytecodeDetails.Builder();

    public BytecodeClassVisitor() {
        super(ASM9);
    }

    public BytecodeDetails getBytecodeClass() {
        return bytecodeDetailsBuilder.build();
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        bytecodeDetailsBuilder.setName(BytecodeUtils.getShortName(name));
        bytecodeDetailsBuilder.setAccess(access);
        bytecodeDetailsBuilder.setExtendsType(BytecodeUtils.getShortName(superName));
        Arrays.stream(interfaces).map(BytecodeUtils::getShortName).map(bytecodeDetailsBuilder::addInterface);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        desc = BytecodeUtils.getShortDesc(desc);

        AnnotationDetails annotationDetails = new AnnotationDetails(desc, visible);
        bytecodeDetailsBuilder.addAnnotation(annotationDetails);

        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        return new BytecodeAnnotationVisitor(ASM9, av, annotationDetails);
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        name = BytecodeUtils.getShortName(name);
        outerName = outerName != null ? BytecodeUtils.getShortName(outerName) : null;
        NestedClassDetails nestedClassDetails = new NestedClassDetails(name, outerName, innerName, access);
        bytecodeDetailsBuilder.addInnerClass(nestedClassDetails);
    }

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        desc = BytecodeUtils.getShortDesc(desc);

        FieldDetails fieldDetails = new FieldDetails(name, desc);
        bytecodeDetailsBuilder.addField(fieldDetails);

        FieldVisitor originalVisitor = super.visitField(access, name, desc, signature, value);
        return new BytecodeFieldVisitor(api, originalVisitor, fieldDetails);
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        desc = BytecodeUtils.getShortDesc(desc);
        exceptions = exceptions != null ? Arrays.stream(exceptions).map(BytecodeUtils::getShortName).toArray(String[]::new) : null;

        MethodDetails method = null;
        if ("<init>".equals(name)) {
            // This is a constructor
            ConstructorDetails constructor = new ConstructorDetails(name, desc, exceptions);
            bytecodeDetailsBuilder.addConstructor(constructor);
        } else {
            method = new MethodDetails(access, name, desc, exceptions);
            Type methodType = Type.getMethodType(desc);
            for (Type argType : methodType.getArgumentTypes()) {
                method.addArgumentType(argType.getClassName());
            }
            method.setReturnType(methodType.getReturnType().getClassName());
            bytecodeDetailsBuilder.addMethod(method);
        }

        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return method != null ? new BytecodeMethodVisitor(ASM9, mv, method) : mv;
    }
}