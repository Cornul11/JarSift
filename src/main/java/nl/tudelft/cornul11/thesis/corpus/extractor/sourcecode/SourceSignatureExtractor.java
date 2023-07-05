package nl.tudelft.cornul11.thesis.corpus.extractor.sourcecode;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.visitor.VoidVisitor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class SourceSignatureExtractor {
    public static JavaClass run(String filename) throws FileNotFoundException {
        FileInputStream in = new FileInputStream(filename);
        CompilationUnit cu = StaticJavaParser.parse(in);

        return extractSignature(cu);
    }

    private static JavaClass extractSignature(CompilationUnit cu) {
        JavaClass javaClass = new JavaClass();
        VoidVisitor<JavaClass> sourceSignatureVisitorCollector = new SourceSignatureVisitorCollector();
        sourceSignatureVisitorCollector.visit(cu, javaClass);
        System.out.println("Source signature:");
        System.out.println(javaClass.name + " " + javaClass.extendsType);

        return javaClass;
    }

    private static void printMethodSignature(CompilationUnit cu) {
        VoidVisitor<Void> sourceSignatureVisitor = new SourceSignatureVisitorPrinter();
        sourceSignatureVisitor.visit(cu, null);
    }
}
