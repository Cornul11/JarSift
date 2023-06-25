package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;

import java.util.ArrayList;
import java.util.List;

import net.openhft.hashing.LongHashFunction;

public class BytecodeDetails {
    public String name;
    public String extendsType;
    public List<String> interfaces = new ArrayList<>();
    public List<FieldDetails> fieldDetails = new ArrayList<>();
    public List<MethodDetails> methods = new ArrayList<>();
    public List<ConstructorDetails> constructors = new ArrayList<>();
    public List<InterfaceDetails> innerInterfaces = new ArrayList<>();
    public List<EnumDetails> innerEnums = new ArrayList<>();
    public List<AnnotationDetails> annotations = new ArrayList<>();

    public long getSignature() {
        // TODO: move the hashing function outside of this class
        // this function should only return the signature, not hash it

        LongHashFunction cityHashFunction = LongHashFunction.xx3();
        StringBuilder classSignature = new StringBuilder();

        classSignature.append(extendsType);
        classSignature.append(interfaces.toString());
        classSignature.append(fieldDetails.toString());
        classSignature.append(methods.toString());
        classSignature.append(constructors.toString());
        classSignature.append(innerInterfaces.toString());
        classSignature.append(innerEnums.toString());
        classSignature.append(annotations.toString());
        return cityHashFunction.hashChars(classSignature);
    }
}