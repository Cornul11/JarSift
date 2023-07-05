package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode;

import org.objectweb.asm.ClassReader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

public class BytecodeParser {
    public static BytecodeDetails run(Path classFilePath) throws IOException {
        byte[] bytecode = readClassFile(classFilePath);
        return extractSignature(bytecode);
    }

    private static byte[] readClassFile(Path classFilePath) throws IOException {
        FileInputStream input = new FileInputStream(classFilePath.toFile());
        byte[] bytecode = input.readAllBytes();
        input.close();
        return bytecode;
    }

    public static BytecodeDetails extractSignature(byte[] bytecode) throws RuntimeException {
        ClassReader classReader = new ClassReader(bytecode);
        BytecodeClassVisitor bytecodeClassVisitor = new BytecodeClassVisitor();
        try {
            classReader.accept(bytecodeClassVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            throw new RuntimeException("Error while parsing bytecode", e);
        }
        return bytecodeClassVisitor.getBytecodeClass();
    }
}