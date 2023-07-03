package nl.tudelft.cornul11.thesis.file;

public class ClassFileInfo {
    private final String className;
    private final long hashCode;
    private final long crc;

    public ClassFileInfo(String className, long hashCode, long crc) {
        this.className = className;
        this.hashCode = hashCode;
        this.crc = crc;
    }

    public String getClassName() {
        return className;
    }

    public long getHashCode() {
        return hashCode;
    }

    public long getCrc() {
        return crc;
    }
}