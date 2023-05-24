package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;
import org.objectweb.asm.*;

import java.util.Arrays;

import static org.objectweb.asm.Opcodes.ASM8;

public class BytecodeSignatureVisitor extends ClassVisitor {
    private BytecodeClass bytecodeClass = new BytecodeClass();

    public BytecodeSignatureVisitor() {
        super(ASM8);
    }

    public BytecodeClass getBytecodeClass() {
        return bytecodeClass;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        bytecodeClass.name = name;
        bytecodeClass.extendsType = superName;
        bytecodeClass.interfaces.addAll(Arrays.asList(interfaces));
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public void visitSource(String source, String debug) {
        // TODO:
    }

    public void visitOuterClass(String owner, String name, String desc) {
        // TODO:

    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        BytecodeAnnotation bytecodeAnnotation = new BytecodeAnnotation();
        bytecodeAnnotation.desc = desc;
        bytecodeAnnotation.visible = visible;
        bytecodeClass.annotations.add(bytecodeAnnotation);
        return super.visitAnnotation(desc, visible);
    }

    public void visitAttribute(Attribute attr) {
        // TODO:
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        BytecodeInterface bytecodeInterface = new BytecodeInterface();
        bytecodeInterface.name = name;
        bytecodeInterface.outerName = outerName;
        bytecodeInterface.innerName = innerName;
        bytecodeInterface.access = access;
        bytecodeClass.innerInterfaces.add(bytecodeInterface);
    }

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        Field field = new Field();
        field.name = name;
        field.desc = desc;
        bytecodeClass.fields.add(field);
        return super.visitField(access, name, desc, signature, value);
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if ("<init>".equals(name)) {
            JavaConstructor constructor = new JavaConstructor();
            constructor.name = name;
            constructor.desc = desc;
            constructor.signature = signature;
            constructor.exceptions = exceptions;
            bytecodeClass.constructors.add(constructor);
        } else {
            JavaMethod method = new JavaMethod();
            method.name = name;
            method.desc = desc;
            method.signature = signature;
            method.exceptions = exceptions;
            bytecodeClass.methods.add(method);
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    public void visitEnd() {
        // nothing needs to be done apparently
    }
}