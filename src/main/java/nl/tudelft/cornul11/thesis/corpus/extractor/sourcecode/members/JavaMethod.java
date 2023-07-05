package nl.tudelft.cornul11.thesis.corpus.extractor.sourcecode.members;

import java.util.ArrayList;
import java.util.List;

public class JavaMethod {
    public String name;
    public String returnType;
    public List<JavaParameter> parameters = new ArrayList<>();
}