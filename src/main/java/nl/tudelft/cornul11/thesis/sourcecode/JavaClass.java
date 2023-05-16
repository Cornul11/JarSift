package nl.tudelft.cornul11.thesis.sourcecode;

import nl.tudelft.cornul11.thesis.sourcecode.members.JavaField;
import nl.tudelft.cornul11.thesis.sourcecode.members.JavaConstructor;
import nl.tudelft.cornul11.thesis.sourcecode.members.JavaMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JavaClass {
    public String name;
    public String extendsType = "java.lang.Object";
    public List<String> implementsTypes = new ArrayList<>();
    public List<JavaField> fields = new ArrayList<>();
    public List<JavaMethod> methods = new ArrayList<>();
    public List<JavaConstructor> constructors = new ArrayList<>();

    public void setName(String fullyQualifiedName) {
        this.name = fullyQualifiedName;
    }

    @Override
    public int hashCode() {
        String binaryNameForm = name.replace('.', '/');
        String binaryExtendsTypeForm = extendsType = extendsType.replace('.', '/');
        return Objects.hash(binaryNameForm, binaryExtendsTypeForm);
    }
}