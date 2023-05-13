package org.example;

import java.util.List;

public class GuineaClass {

    // Fields
    private int field1;
    private String field2;
    public static final int CONSTANT_VALUE = 100;

    // Constructors
    public GuineaClass() {
        this.field1 = 0;
        this.field2 = "default";
    }

    public GuineaClass(int field1, String field2) {
        this.field1 = field1;
        this.field2 = field2;
    }

    // Methods
    public void voidMethod() {
        System.out.println("This is a void method.");
    }

    public int intMethod(int num1, int num2) {
        return num1 + num2;
    }

    public String stringMethod(String input) {
        return "Hello, " + input;
    }

    public List<String> listMethod() {
        // Method implementation
        return null;
    }

    // Annotations
    @Override
    public String toString() {
        return "ExampleClass[field1=" + field1 + ", field2=" + field2 + "]";
    }

    @Deprecated
    public void deprecatedMethod() {
        // Deprecated method implementation
    }
}