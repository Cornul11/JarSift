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
        // check if the inner class is an interface, if yes
        // add it to the list of inner interfaces
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            BytecodeInterface bytecodeInterface = new BytecodeInterface();
            bytecodeInterface.name = name;
            bytecodeInterface.outerName = outerName;
            bytecodeInterface.innerName = innerName;
            bytecodeInterface.access = access;
            bytecodeClass.innerInterfaces.add(bytecodeInterface);
        } else if ((access & Opcodes.ACC_ENUM) != 0) {
            // shouldn't forget that all inner classes and interfaces are compiled to separate .class files
            // maybe the hash of a class should be the hash of all its inner classes and interfaces?
            // TODO: consider looking at the class file name, and to group by everything up to $ in the name of the class
            BytecodeEnum bytecodeEnum = new BytecodeEnum();
            bytecodeEnum.name = name;
            bytecodeEnum.outerName = outerName;
            bytecodeEnum.innerName = innerName;
            bytecodeEnum.access = access;
            System.out.println(access);
            bytecodeClass.innerEnums.add(bytecodeEnum);
        }
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