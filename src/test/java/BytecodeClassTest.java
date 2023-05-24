import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;
public class BytecodeClassTest {
    @Test
    public void testInterfaceAnnotationDifference() throws IOException {
        // TODO: this is very ugly and hacky, got to improve it later

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("interface/annotation/ClassWithAnnotation.class");
        assert inputStream != null;
        byte[] bytecode = inputStream.readAllBytes();

        BytecodeClass withAnnotations = BytecodeSignatureExtractor.extractSignature(bytecode);

        inputStream = getClass().getClassLoader().getResourceAsStream("interface/annotation/ClassWithAnnotation.class");
        assert inputStream != null;
        bytecode = inputStream.readAllBytes();

        BytecodeClass withoutAnnotations = BytecodeSignatureExtractor.extractSignature(bytecode);

        assertNotEquals(withAnnotations.annotations.size(), withoutAnnotations.annotations.size());
        assertNotEquals(withAnnotations.hashCode(), withoutAnnotations.hashCode());
    }


    @Test
    public void testMethodCountDifference() throws IOException {
        // TODO: this is very ugly and hacky, got to improve it later

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("interface/method/ClassWithMethods.class");
        assert inputStream != null;
        byte[] bytecode = inputStream.readAllBytes();

        BytecodeClass withMethods = BytecodeSignatureExtractor.extractSignature(bytecode);

        inputStream = getClass().getClassLoader().getResourceAsStream("interface/method/ClassWithOneLessMethods.class");
        assert inputStream != null;
        bytecode = inputStream.readAllBytes();

        BytecodeClass withOneLessMethods = BytecodeSignatureExtractor.extractSignature(bytecode);

        assertNotEquals(withMethods.methods.size(), withOneLessMethods.methods.size());
        assertNotEquals(withMethods.hashCode(), withOneLessMethods.hashCode());
    }
}
