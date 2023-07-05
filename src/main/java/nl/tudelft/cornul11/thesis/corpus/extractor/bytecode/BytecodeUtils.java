package nl.tudelft.cornul11.thesis.corpus.extractor.bytecode;

import net.openhft.hashing.LongHashFunction;
import nl.tudelft.cornul11.thesis.corpus.extractor.bytecode.members.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarEntry;
import java.util.zip.CRC32;

public class BytecodeUtils {
    public static byte[] readBytecodeAndCalculateCRCWhenNotAvailable(JarEntry entry, InputStream classFileInputStream) throws IOException {
        byte[] bytecode = classFileInputStream.readAllBytes();
        if (entry.getCrc() == -1) {
            CRC32 crc = new CRC32();
            crc.update(bytecode);
            entry.setCrc(crc.getValue());
        }
        return bytecode;
    }

    public static long getSignatureHash(BytecodeDetails bytecodeDetails) {
        LongHashFunction cityHashFunction = LongHashFunction.xx3();
        StringBuilder classSignature = new StringBuilder();

        classSignature.append(bytecodeDetails.getAccess());
        classSignature.append(bytecodeDetails.getName());
        classSignature.append(bytecodeDetails.getExtendsType());

        for (String iface : bytecodeDetails.getInterfaces()) {
            classSignature.append(iface);
        }

        for (FieldDetails field : bytecodeDetails.getFields()) {
            classSignature.append(field.toSignaturePart());
        }

        for (MethodDetails method : bytecodeDetails.getMethods()) {
            classSignature.append(method.toSignaturePart());
        }

        for (ConstructorDetails constructor : bytecodeDetails.getConstructors()) {
            classSignature.append(constructor.toSignaturePart());
        }

        for (NestedClassDetails nestedClass : bytecodeDetails.getInnerClasses()) {
            classSignature.append(nestedClass.toSignaturePart());
        }

        for (AnnotationDetails annotation : bytecodeDetails.getAnnotations()) {
            classSignature.append(annotation.toSignaturePart());
        }

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

    public static String getShortDesc(String desc) {
        StringBuilder sb = new StringBuilder();
        int start = 0;
        int descLen = desc.length();

        while (start < descLen) {
            int lIndex = desc.indexOf('L', start);
            if (lIndex == -1) {
                sb.append(desc, start, descLen);
                break;
            }
            sb.append(desc, start, lIndex + 1);
            start = lIndex + 1;

            int semicolonIndex = desc.indexOf(';', start);
            if (semicolonIndex == -1) {
                sb.append(desc, start, descLen);
                break;
            }

            String fullName = desc.substring(start, semicolonIndex);
            String shortName;
            int genericStart = fullName.indexOf('<');
            if (genericStart != -1) {
                String baseName = fullName.substring(0, genericStart);
                String genericPart = fullName.substring(genericStart);
                shortName = getShortName(baseName) + processGenericPart(genericPart);
            } else {
                shortName = getShortName(fullName);
            }

            sb.append(shortName);
            start = semicolonIndex;
        }

        return sb.toString();
    }

    public static String getShortName(String className) {
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
