package nl.tudelft.cornul11.thesis;

import nl.tudelft.cornul11.thesis.bytecode.BytecodeClass;
import nl.tudelft.cornul11.thesis.bytecode.BytecodeSignatureExtractor;
import nl.tudelft.cornul11.thesis.comparator.HashComparator;
import nl.tudelft.cornul11.thesis.sourcecode.JavaClass;
import nl.tudelft.cornul11.thesis.sourcecode.SourceSignatureExtractor;

import java.io.IOException;

public class EntryPoint {
    public static void main(String[] args) throws IOException {
        // extracts signature from a .class file
        BytecodeClass bytecodeClass = BytecodeSignatureExtractor.run("GuineaClass");

        // extract signature from a .java file
        JavaClass javaClass = SourceSignatureExtractor.run("GuineaClass.java");

        boolean equal = HashComparator.compare(bytecodeClass.hashCode(), javaClass.hashCode());

        if (equal) {
            System.out.println("Signatures are equal");
        } else {
            System.out.println("Signatures are not equal");
        }
    }
}
