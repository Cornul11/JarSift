package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode;

import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.*;

import java.util.ArrayList;
import java.util.List;

public class BytecodeDetails {
    private int access;
    private String name;
    private String extendsType;
    private List<String> interfaces;
    private List<FieldDetails> fields;
    private List<MethodDetails> methods;
    private List<ConstructorDetails> constructors;
    private List<NestedClassDetails> innerClasses;
    private List<AnnotationDetails> annotations;
    private int majorVersion;

    private BytecodeDetails(Builder builder) {
        this.access = builder.access;
        this.name = builder.name;
        this.extendsType = builder.extendsType;
        this.interfaces = builder.interfaces;
        this.fields = builder.fields;
        this.methods = builder.methods;
        this.constructors = builder.constructors;
        this.innerClasses = builder.innerClasses;
        this.annotations = builder.annotations;
        this.majorVersion = builder.majorVersion;
    }

    public String getName() {
        return name;
    }

    public String getExtendsType() {
        return extendsType;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<FieldDetails> getFields() {
        return fields;
    }

    public List<MethodDetails> getMethods() {
        return methods;
    }

    public List<ConstructorDetails> getConstructors() {
        return constructors;
    }

    public List<NestedClassDetails> getInnerClasses() {
        return innerClasses;
    }

    public List<AnnotationDetails> getAnnotations() {
        return annotations;
    }

    public int getAccess() {
        return access;
    }

    public int getMajorVersion() {
        return majorVersion;
    }

    public static class Builder {
        private int access;
        private String name;
        private String extendsType;
        private List<String> interfaces = new ArrayList<>();
        private List<FieldDetails> fields = new ArrayList<>();
        private List<MethodDetails> methods = new ArrayList<>();
        private List<ConstructorDetails> constructors = new ArrayList<>();
        private List<NestedClassDetails> innerClasses = new ArrayList<>();
        private List<AnnotationDetails> annotations = new ArrayList<>();
        private int majorVersion;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setExtendsType(String extendsType) {
            this.extendsType = extendsType;
            return this;
        }

        public Builder addInterface(String interfaceName) {
            this.interfaces.add(interfaceName);
            return this;
        }

        public Builder addField(FieldDetails field) {
            this.fields.add(field);
            return this;
        }

        public Builder addMethod(MethodDetails method) {
            this.methods.add(method);
            return this;
        }

        public Builder addConstructor(ConstructorDetails constructor) {
            this.constructors.add(constructor);
            return this;
        }

        public Builder addInnerClass(NestedClassDetails innerClass) {
            this.innerClasses.add(innerClass);
            return this;
        }

        public Builder addAnnotation(AnnotationDetails annotation) {
            this.annotations.add(annotation);
            return this;
        }

        public Builder setAccess(int access) {
            this.access = access;
            return this;
        }

        public Builder setMajorVersion(int majorVersion) {
            this.majorVersion = majorVersion;
            return this;
        }

        public BytecodeDetails build() {
            return new BytecodeDetails(this);
        }
    }
}