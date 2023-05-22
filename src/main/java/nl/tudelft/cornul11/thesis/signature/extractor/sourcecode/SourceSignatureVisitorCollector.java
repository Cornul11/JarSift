package nl.tudelft.cornul11.thesis.signature.extractor.sourcecode;

import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import nl.tudelft.cornul11.thesis.signature.extractor.sourcecode.members.JavaField;
import nl.tudelft.cornul11.thesis.signature.extractor.sourcecode.members.JavaConstructor;
import nl.tudelft.cornul11.thesis.signature.extractor.sourcecode.members.JavaParameter;
import nl.tudelft.cornul11.thesis.signature.extractor.sourcecode.members.JavaMethod;


public class SourceSignatureVisitorCollector extends VoidVisitorAdapter<JavaClass> {
    @Override
    public void visit(ClassOrInterfaceDeclaration cls, JavaClass javaClass) {
        super.visit(cls, javaClass);
        // records, and local classes return Optional.empty()
        javaClass.setName(cls.getFullyQualifiedName().orElse(cls.getName().asString()));

        cls.getExtendedTypes().forEach(extendedType -> {
            javaClass.extendsType = extendedType.getNameAsString();
        });

        cls.getImplementedTypes().forEach(implementedType -> {
            javaClass.implementsTypes.add(implementedType.getNameAsString());
        });

        cls.getFields().forEach(field -> {
            JavaField newField = new JavaField();
            newField.name = field.getVariables().get(0).getNameAsString();
            newField.type = field.getElementType().asString();
            newField.accessModifier = field.getAccessSpecifier().asString();
            javaClass.fields.add(newField);
        });
    }

    @Override
    public void visit(ConstructorDeclaration constructor, JavaClass javaClass) {
        super.visit(constructor, javaClass);

        JavaConstructor newConstructor = new JavaConstructor();
        newConstructor.name = constructor.getNameAsString();

        constructor.getParameters().forEach(param -> {
            JavaParameter newParam = new JavaParameter();
            newParam.name = param.getNameAsString();
            newParam.type = param.getTypeAsString();
            newConstructor.parameters.add(newParam);
        });

        javaClass.constructors.add(newConstructor);
    }

    @Override
    public void visit(MethodDeclaration method, JavaClass javaClass) {
        super.visit(method, javaClass);

        JavaMethod newMethod = new JavaMethod();
        newMethod.name = method.getNameAsString();
        newMethod.returnType = method.getTypeAsString();

        method.getParameters().forEach(param -> {
            JavaParameter newParam = new JavaParameter();
            newParam.name = param.getNameAsString();
            newParam.type = param.getTypeAsString();
            newMethod.parameters.add(newParam);
        });

        javaClass.methods.add(newMethod);
    }
}