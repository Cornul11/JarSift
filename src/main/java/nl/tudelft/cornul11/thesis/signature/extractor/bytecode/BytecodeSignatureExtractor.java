package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import org.objectweb.asm.ClassReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

public class BytecodeSignatureExtractor {
    public static BytecodeClass run(Path classFilePath) throws IOException {
        byte[] bytecode = readClassFile(classFilePath);
        return extractSignature(bytecode);
    }

    private static byte[] readClassFile(Path classFilePath) throws IOException {
        FileInputStream input = new FileInputStream(classFilePath.toFile());
        byte[] bytecode = input.readAllBytes();
        input.close();
        return bytecode;
    }

    public static BytecodeClass extractSignature(byte[] bytecode) {
        ClassReader classReader = new ClassReader(bytecode);
        BytecodeSignatureVisitor bytecodeSignatureVisitor = new BytecodeSignatureVisitor();
        classReader.accept(bytecodeSignatureVisitor, ClassReader.EXPAND_FRAMES); // TODO: why EXPAND FRAMES

        return bytecodeSignatureVisitor.getBytecodeClass();

    }
}