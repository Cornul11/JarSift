package nl.tudelft.cornul11.thesis.file;

public class ClassFileInfo {
    private final String fileName;
    private final long hashCode;
    private final long crc;

    public ClassFileInfo(String fileName, long hashCode, long crc) {
        this.fileName = fileName;
        this.hashCode = hashCode;
        this.crc = crc;
    }

    public String getFileName() {
        return fileName;
    }

    public long getHashCode() {
        return hashCode;
    }

    public long getCrc() {
        return crc;
    }
}