package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;

import java.util.ArrayList;
import java.util.List;

import net.openhft.hashing.LongHashFunction;

public class BytecodeClass {
    public String name;
    public String extendsType;
    public List<String> interfaces = new ArrayList<>();
    public List<Field> fields = new ArrayList<>();
    public List<JavaMethod> methods = new ArrayList<>();
    public List<JavaConstructor> constructors = new ArrayList<>();
    public List<BytecodeInterface> innerInterfaces = new ArrayList<>();
    public List<BytecodeEnum> innerEnums = new ArrayList<>();
    public List<BytecodeAnnotation> annotations = new ArrayList<>();

    public long getSignature() {
        // TODO: move the hashing function outside of this class
        // this function should only return the signature, not hash it
        // TODO: work on the actual signature to take into account more things so that
        // the signature is more unique

        LongHashFunction cityHashFunction = LongHashFunction.xx3();
        // TODO: maybe hash all separate fields and then hash them together to create a per-version signature?
        StringBuilder classSignature = new StringBuilder();
        classSignature.append(name);
        classSignature.append(extendsType);
        classSignature.append(interfaces.toString());
        classSignature.append(fields.toString());
        classSignature.append(methods.toString());
        classSignature.append(constructors.toString());
        classSignature.append(innerInterfaces.toString());
        classSignature.append(innerEnums.toString());
        classSignature.append(annotations.toString());
        return cityHashFunction.hashChars(classSignature);
    }
}