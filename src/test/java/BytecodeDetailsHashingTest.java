import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeDetails;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeParser;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.BytecodeUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

// TODO: add more tests for different class features
public class BytecodeDetailsHashingTest {
    private static final String CLASS_WITH_PACKAGE_PRIVATE_INNER_ENUM_CLASS = "class/inner/enum/ClassWithPackagePrivateInnerEnum.class";
    private static final String CLASS_WITH_ANNOTATIONS = "interface/annotation/ClassWithAnnotation.class";
    private static final String CLASS_WITHOUT_ANNOTATIONS = "interface/annotation/ClassWithoutAnnotation.class";
    private static final String CLASS_WITH_PRIVATE_INNER_ENUM_CLASS = "class/inner/enum/ClassWithPrivateInnerEnum.class";
    private static final String CLASS_WITH_METHODS_CLASS = "interface/method/ClassWithMethods.class";
    private static final String CLASS_WITH_ONE_LESS_METHODS_CLASS = "interface/method/ClassWithOneLessMethods.class";

    // TODO: these classes could maybe be generated locally instead of being read from the resources folder
    @Test
    public void testInterfaceAnnotationDifference() throws IOException {
        BytecodeDetails withAnnotations = loadBytecodeClass(CLASS_WITH_ANNOTATIONS);
        BytecodeDetails withoutAnnotations = loadBytecodeClass(CLASS_WITHOUT_ANNOTATIONS);

        assertNotEquals(withAnnotations.getAnnotations().size(), withoutAnnotations.getAnnotations().size(), "The number of annotations should be different between the two classes.");
        assertNotEquals(BytecodeUtils.getSignatureHash(withAnnotations), BytecodeUtils.getSignatureHash(withoutAnnotations), "The hashcodes of the two classes should be different.");
    }

    // TODO: disable for now until it works
//    @Test
    public void testInnerEnumAccessDifference() throws IOException {
        // TODO: this is very ugly and hacky, got to improve it later
        // TODO: this does not yet spot the difference between package private and private

        BytecodeDetails packagePrivateClass = loadBytecodeClass(CLASS_WITH_PACKAGE_PRIVATE_INNER_ENUM_CLASS);
        BytecodeDetails privateClass = loadBytecodeClass(CLASS_WITH_PRIVATE_INNER_ENUM_CLASS);

        assertNotEquals(BytecodeUtils.getSignatureHash(packagePrivateClass), BytecodeUtils.getSignatureHash(privateClass), "The hashcodes of the two classes should be different.");
    }


    @Test
    public void testMethodCountDifference() throws IOException {
        // TODO: this is very ugly and hacky, got to improve it later

        BytecodeDetails withMethods = loadBytecodeClass(CLASS_WITH_METHODS_CLASS);
        BytecodeDetails withOneLessMethods = loadBytecodeClass(CLASS_WITH_ONE_LESS_METHODS_CLASS);

        assertNotEquals(withMethods.getMethods().size(), withOneLessMethods.getMethods().size(), "The number of methods should be different between the two classes");
        assertNotEquals(BytecodeUtils.getSignatureHash(withMethods), BytecodeUtils.getSignatureHash(withOneLessMethods), "The hashcodes of the two classes should be different");
    }

//    @Test
//    public void testSomething() throws IOException {
//        BytecodeDetails xx1 = loadBytecodeClass(CLASS_1);
//        BytecodeDetails xx2 = loadBytecodeClass(CLASS_2);
//
//
//    }

    private BytecodeDetails loadBytecodeClass(String resourceName) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IllegalArgumentException("Resource not found: " + resourceName);
        }
        byte[] bytecode = inputStream.readAllBytes();
        return BytecodeParser.extractSignature(bytecode);
    }
}
