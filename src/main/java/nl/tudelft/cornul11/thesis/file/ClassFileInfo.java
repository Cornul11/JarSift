package nl.tudelft.cornul11.thesis.file;

public class ClassFileInfo {
    private final String fileName;
    private final long hashCode;

    public ClassFileInfo(String fileName, long hashCode) {
        this.fileName = fileName;
        this.hashCode = hashCode;
    }

    public String getFileName() {
        return fileName;
    }

    public long getHashCode() {
        return hashCode;
    }
}