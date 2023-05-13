package org.example;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.io.FileInputStream;
import java.io.IOException;

public class SignatureExtractor {
    public static void main(String[] args) throws IOException {
        String className = "GuineaClass";
        byte[] bytecode = readClassFile(className);
        extractSignature(bytecode);
    }

    private static byte[] readClassFile(String className) throws IOException {
        String classFilePath = className.replace('.', '/') + ".class";

        FileInputStream input = new FileInputStream(classFilePath);
        byte[] bytecode = input.readAllBytes();
        input.close();
        return bytecode;
    }

    private static void extractSignature(byte[] bytecode) {
        ClassReader cr = new ClassReader(bytecode);
        ClassVisitor cv = new SignatureVisitor();
        cr.accept(cv, ClassReader.EXPAND_FRAMES); // why EXPAND FRAMES
    }
}