package nl.tudelft.cornul11.thesis.signature.extractor.sourcecode;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class SourceSignatureVisitorPrinter extends VoidVisitorAdapter<Void> {
    @Override
    public void visit(MethodDeclaration md, Void arg) {
        super.visit(md, arg);
        System.out.println("Method name printed: " + md.getName());
    }
}