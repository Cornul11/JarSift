package org.example;

import org.objectweb.asm.*;

import java.util.Arrays;

import static org.objectweb.asm.Opcodes.ASM7;

public class SignatureVisitor extends ClassVisitor {
    public SignatureVisitor() {
        super(ASM7);
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        System.out.println(name + " extends " + superName + " {");
    }

    public void visitSource(String source, String debug) {
        System.out.println("SOURCE:  " + source + " " + debug);
    }

    public void visitOuterClass(String owner, String name, String desc) {
        System.out.println("OUTERCLASS: " + owner + " " + name + " " + desc);

    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        System.out.println("ANNOTATION: " + desc + " " + visible);
        return null;
    }

    public void visitAttribute(Attribute attr) {
        System.out.println("ATTR: " + attr);
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        System.out.println("INNER CLASS:    " + name + " " + outerName + " " + innerName + " " + access);
    }

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        System.out.println("FIELD:    " + desc + " " + name);
        return null;
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        System.out.println("METHOD:    " + name + " " + desc + " " + signature);
        System.out.println(Arrays.toString(exceptions));
        return null;
    }

    public void visitEnd() {
        System.out.println("}");
    }
}
