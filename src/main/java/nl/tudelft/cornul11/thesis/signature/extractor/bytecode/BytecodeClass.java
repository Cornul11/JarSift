package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import nl.tudelft.cornul11.thesis.signature.extractor.bytecode.members.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    @Override
    public int hashCode() {
        // TODO: maybe hash all separate fields and then hash them together to create a per-version signature?

        // if name contains string "s"
        if (name.contains("TreeBidiMap")) {
            System.out.println("Found class: " + name);
        }

        return Objects.hash(name, annotations, extendsType, methods.size());
    }
}