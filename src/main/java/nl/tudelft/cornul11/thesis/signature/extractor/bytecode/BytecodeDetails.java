package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import net.openhft.hashing.LongHashFunction;
import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;

import java.util.ArrayList;
import java.util.List;

public class BytecodeDetails {
    public String name;
    public String extendsType;
    public List<String> interfaces = new ArrayList<>();
    public List<FieldDetails> fields = new ArrayList<>();
    public List<MethodDetails> methods = new ArrayList<>();
    public List<ConstructorDetails> constructors = new ArrayList<>();
    public List<NestedClassDetails> innerClasses = new ArrayList<>();
    public List<AnnotationDetails> annotations = new ArrayList<>();


    //
//    private void preProcessNames() {
//        for (AnnotationDetails annotation : annotations) {
//            for (Map.Entry<String, AnnotationDetails> entry : annotation.annotationArguments.entrySet()) {
//                entry.getValue().desc = getShortDesc(entry.getValue().desc);
//            }
//        }
//
//        // Similar processing should be done for the names inside innerInterfaces, innerEnums, and annotations
//    }
    public long getSignature() {
        // TODO: move the hashing function outside of this class
        // this function should only return the signature, not hash it

        LongHashFunction cityHashFunction = LongHashFunction.xx3();
        StringBuilder classSignature = new StringBuilder();

        // Considering the name is now shortened (loses package name), we can add it to the signature
        classSignature.append(name);
        classSignature.append(extendsType);
        classSignature.append(interfaces.toString());
        classSignature.append(fields.toString());
        classSignature.append(methods.toString());
        classSignature.append(constructors.toString());
        classSignature.append(innerClasses.toString());
        classSignature.append(annotations.toString());
        return cityHashFunction.hashChars(classSignature);
    }
}