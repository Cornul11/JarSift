package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;
import org.objectweb.asm.*;

import java.util.Arrays;

import static org.objectweb.asm.Opcodes.ASM8;

public class BytecodeClassVisitor extends ClassVisitor {
    private final BytecodeDetails bytecodeDetails = new BytecodeDetails();

    public BytecodeClassVisitor() {
        super(ASM8);
    }

    public BytecodeDetails getBytecodeClass() {
        return bytecodeDetails;
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        bytecodeDetails.name = name;
        bytecodeDetails.extendsType = superName;
        bytecodeDetails.interfaces.addAll(Arrays.asList(interfaces));
        super.visit(version, access, name, signature, superName, interfaces);
    }

    public void visitSource(String source, String debug) {
        // TODO:
    }

    public void visitOuterClass(String owner, String name, String desc) {
        // TODO:
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationDetails annotationDetails = new AnnotationDetails();
        annotationDetails.desc = desc;
        annotationDetails.visible = visible;
        bytecodeDetails.annotations.add(annotationDetails);

        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        return new BytecodeAnnotationVisitor(ASM8, av, annotationDetails);
    }

    public void visitAttribute(Attribute attr) {
        // TODO:
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        // check if the inner class is an interface, if yes
        // add it to the list of inner interfaces
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            InterfaceDetails interfaceDetails = new InterfaceDetails();
            interfaceDetails.name = name;
            interfaceDetails.outerName = outerName;
            interfaceDetails.innerName = innerName;
            interfaceDetails.access = access;
            bytecodeDetails.innerInterfaces.add(interfaceDetails);
        } else if ((access & Opcodes.ACC_ENUM) != 0) {
            // shouldn't forget that all inner classes and interfaces are compiled to separate .class files
            // maybe the hash of a class should be the hash of all its inner classes and interfaces?
            // TODO: consider looking at the class file name, and to group by everything up to $ in the name of the class
            EnumDetails enumDetails = new EnumDetails();
            enumDetails.name = name;
            enumDetails.outerName = outerName;
            enumDetails.innerName = innerName;
            enumDetails.access = access;
            bytecodeDetails.innerEnums.add(enumDetails);
        }
    }

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        FieldDetails fieldDetails = new FieldDetails();
        fieldDetails.name = name;
        fieldDetails.desc = desc;
        bytecodeDetails.fieldDetails.add(fieldDetails);
        return super.visitField(access, name, desc, signature, value);
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodDetails method = new MethodDetails();
        method.name = name;
        method.setDesc(desc);
        method.signature = signature;
        method.exceptions = exceptions;

        if ("<init>".equals(name)) {
            // This is a constructor
            ConstructorDetails constructor = new ConstructorDetails();
            constructor.name = name;
            constructor.desc = desc;
            constructor.signature = signature;
            constructor.exceptions = exceptions;
            bytecodeDetails.constructors.add(constructor);
        } else {
            // This is a method
            bytecodeDetails.methods.add(method);
        }

        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        return new BytecodeMethodVisitor(ASM8, mv, method);
    }

    public void visitEnd() {
        // nothing needs to be done apparently
    }
}