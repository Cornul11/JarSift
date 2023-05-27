import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeSignatureExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class BytecodeClassHashingTest {
    private static final String CLASS_WITH_PACKAGE_PRIVATE_INNER_ENUM_CLASS = "class/inner/enum/ClassWithPackagePrivateInnerEnum.class";
    private static final String CLASS_WITH_ANNOTATIONS = "interface/annotation/ClassWithAnnotation.class";
    private static final String CLASS_WITHOUT_ANNOTATIONS = "interface/annotation/ClassWithoutAnnotation.class";
    private static final String CLASS_WITH_PRIVATE_INNER_ENUM_CLASS = "class/inner/enum/ClassWithPrivateInnerEnum.class";
    private static final String CLASS_WITH_METHODS_CLASS = "interface/method/ClassWithMethods.class";
    private static final String CLASS_WITH_ONE_LESS_METHODS_CLASS = "interface/method/ClassWithOneLessMethods.class";

    // TODO: these classes could maybe be generated locally instead of being read from the resources folder
    @Test
    public void testInterfaceAnnotationDifference() throws IOException {
        BytecodeClass withAnnotations = loadBytecodeClass(CLASS_WITH_ANNOTATIONS);
        BytecodeClass withoutAnnotations = loadBytecodeClass(CLASS_WITHOUT_ANNOTATIONS);

        assertNotEquals(withAnnotations.annotations.size(), withoutAnnotations.annotations.size(), "The number of annotations should be different between the two classes.");
        assertNotEquals(withAnnotations.getSignature(), withoutAnnotations.getSignature(), "The hashcodes of the two classes should be different.");
    }

    // TODO: disable for now until it works
//    @Test
    public void testInnerEnumAccessDifference() throws IOException {
        // TODO: this is very ugly and hacky, got to improve it later
        // TODO: this does not yet spot the difference between package private and private

        BytecodeClass packagePrivateClass = loadBytecodeClass(CLASS_WITH_PACKAGE_PRIVATE_INNER_ENUM_CLASS);
        BytecodeClass privateClass = loadBytecodeClass(CLASS_WITH_PRIVATE_INNER_ENUM_CLASS);

        assertNotEquals(packagePrivateClass.getSignature(), privateClass.getSignature(), "The hashcodes of the two classes should be different.");
    }


    @Test
    public void testMethodCountDifference() throws IOException {
        // TODO: this is very ugly and hacky, got to improve it later

        BytecodeClass withMethods = loadBytecodeClass(CLASS_WITH_METHODS_CLASS);
        BytecodeClass withOneLessMethods = loadBytecodeClass(CLASS_WITH_ONE_LESS_METHODS_CLASS);

        assertNotEquals(withMethods.methods.size(), withOneLessMethods.methods.size(), "The number of methods should be different between the two classes");
        assertNotEquals(withMethods.getSignature(), withOneLessMethods.getSignature(), "The hashcodes of the two classes should be different");
    }

    private BytecodeClass loadBytecodeClass(String resourceName) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        byte[] bytecode = inputStream.readAllBytes();
        return BytecodeSignatureExtractor.extractSignature(bytecode);
    }
}
