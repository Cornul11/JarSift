package nl.tudelft.cornul11.thesis.file;

public class ClassFileInfo {
    private final String fileName;
    private final int hashCode;

    public ClassFileInfo(String fileName, int hashCode) {
        this.fileName = fileName;
        this.hashCode = hashCode;
    }

    public String getFileName() {
        return fileName;
    }

    public int getHashCode() {
        return hashCode;
    }
}