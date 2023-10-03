package nl.tudelft.cornul11.thesis.packaging;

import nl.tudelft.cornul11.thesis.corpus.model.Dependency;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;

import java.util.*;

public class LibrarySelector {
    // for now, this method returns a random subset of the libraries in the database
    // with no complex logic or selection patterns
    public LibraryInfo getRandomDependencies(List<Dependency> allLibraries, int numLibraries) {
        if (allLibraries.size() < numLibraries) {
            throw new IllegalArgumentException("Not enough libraries in database");
        }

        Collections.shuffle(allLibraries, new Random());
        return new LibraryInfo(allLibraries.subList(0, numLibraries));
    }
}