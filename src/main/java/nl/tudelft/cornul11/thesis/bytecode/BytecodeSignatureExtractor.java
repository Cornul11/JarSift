package nl.tudelft.cornul11.thesis.bytecode;

import org.objectweb.asm.ClassReader;

import java.io.FileInputStream;
import java.io.IOException;

public class BytecodeSignatureExtractor {
    public static BytecodeClass run(String className) throws IOException {
        byte[] bytecode = readClassFile(className);
        return extractSignature(bytecode);
    }

    private static byte[] readClassFile(String className) throws IOException {
        String classFilePath = className.replace('.', '/') + ".class";

        FileInputStream input = new FileInputStream(classFilePath);
        byte[] bytecode = input.readAllBytes();
        input.close();
        return bytecode;
    }

    private static BytecodeClass extractSignature(byte[] bytecode) {
        ClassReader classReader = new ClassReader(bytecode);
        BytecodeSignatureVisitor bytecodeSignatureVisitor = new BytecodeSignatureVisitor();
        classReader.accept(bytecodeSignatureVisitor, ClassReader.EXPAND_FRAMES); // why EXPAND FRAMES

        BytecodeClass bytecodeClass = bytecodeSignatureVisitor.getBytecodeClass();
        System.out.println("Bytecode signature:");
        System.out.println(bytecodeClass.name + " " + bytecodeClass.extendsType);

        return bytecodeClass;

    }
}