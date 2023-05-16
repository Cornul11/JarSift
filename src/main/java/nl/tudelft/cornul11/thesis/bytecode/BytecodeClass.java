package nl.tudelft.cornul11.thesis.bytecode;

import nl.tudelft.cornul11.thesis.bytecode.members.BytecodeInterface;
import nl.tudelft.cornul11.thesis.bytecode.members.Field;
import nl.tudelft.cornul11.thesis.bytecode.members.JavaConstructor;
import nl.tudelft.cornul11.thesis.bytecode.members.JavaMethod;

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

    @Override
    public int hashCode() {
        return Objects.hash(name, extendsType);
    }
}