package nl.tudelft.cornul11.thesis.signature.extractor.bytecode;

import net.openhft.hashing.LongHashFunction;
import org.jetbrains.annotations.NotNull;

public class BytecodeUtils {
    public static long getSignatureHash(BytecodeDetails bytecodeDetails) {
        LongHashFunction cityHashFunction = LongHashFunction.xx3();
        StringBuilder classSignature = new StringBuilder();

        classSignature.append(bytecodeDetails.getName());
        classSignature.append(bytecodeDetails.getExtendsType());
        classSignature.append(bytecodeDetails.getInterfaces().toString());
        classSignature.append(bytecodeDetails.getFields().toString());
        classSignature.append(bytecodeDetails.getMethods().toString());
        classSignature.append(bytecodeDetails.getConstructors().toString());
        classSignature.append(bytecodeDetails.getInnerClasses().toString());
        classSignature.append(bytecodeDetails.getAnnotations().toString());

        return cityHashFunction.hashChars(classSignature);
    }


    private static String processGenericPart(String genericPart) {
        // Generic parts are enclosed in <>, and can contain class names denoted by L...;
        StringBuilder processed = new StringBuilder();
        int start = 0;
        int classStart = genericPart.indexOf('L', start);
        while (classStart != -1) {
            int classEnd = genericPart.indexOf(';', classStart);
            if (classEnd == -1) {
                break;
            }
            String fullName = genericPart.substring(classStart + 1, classEnd);
            String shortName = getShortName(fullName);

            processed.append(genericPart, start, classStart + 1).append(shortName);
            start = classEnd;

            classStart = genericPart.indexOf('L', start);
        }
        processed.append(genericPart.substring(start));

        return processed.toString();
    }

    public static String getShortDesc(@NotNull String desc) {
        int start = desc.indexOf('L');
        while (start != -1) {
            int end = desc.indexOf(';', start);
            if (end == -1) {
                break;
            }

            // Process class name, considering generic types
            String fullName = desc.substring(start + 1, end);
            String shortName;
            int genericStart = fullName.indexOf('<');
            if (genericStart != -1) {
                String baseName = fullName.substring(0, genericStart);
                String genericPart = fullName.substring(genericStart);
                shortName = getShortName(baseName) + processGenericPart(genericPart);
            } else {
                shortName = getShortName(fullName);
            }

            desc = desc.substring(0, start + 1) + shortName + desc.substring(end);
            start = desc.indexOf('L', start + 1 + shortName.length() - fullName.length());
        }

        return desc;
    }

    public static String getShortName(@NotNull String className) {
        // If the className represents an array type, return it as is
        if (className.endsWith("[]")) {
            return className;
        }

        // Handling bytecode-style array types
        if (className.startsWith("[")) {
            int classStart = className.indexOf('L');
            if (classStart != -1) {
                // If it's an array of objects
                String arrayType = className.substring(0, classStart + 1);
                String arrayClass = className.substring(classStart + 1, className.length() - 1);
                className = arrayType + getShortName(arrayClass) + ";";
            } else {
                // If it's an array of primitives
                return className;
            }
        }

        // Handling generic types: stripping everything before the first "<"
        int genericIndex = className.indexOf('<');
        if (genericIndex != -1) {
            className = className.substring(0, genericIndex);
        }

        // Handling normal types: stripping everything before the last '/'
        int lastSlashIndex = className.lastIndexOf('/');
        if (lastSlashIndex != -1) {
            className = className.substring(lastSlashIndex + 1);
        }

        // Handling normal types: stripping everything before the last '.'
        int lastDotIndex = className.lastIndexOf('.');
        if (lastDotIndex != -1) {
            className = className.substring(lastDotIndex + 1);
        }

        return className;
    }
}
