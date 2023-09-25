package nl.tudelft.cornul11.thesis.packaging;

import nl.tudelft.cornul11.thesis.corpus.database.SignatureDAO;
import nl.tudelft.cornul11.thesis.corpus.model.LibraryInfo;

import java.util.*;

public class LibrarySelector {
    private SignatureDAO signatureDAO;

    public LibrarySelector(SignatureDAO signatureDAO) {
        this.signatureDAO = signatureDAO;
    }

    // for now, this method returns a random subset of the libraries in the database
    // with no complex logic or selection patterns
    public List<LibraryInfo> getRandomLibraries(int numLibraries) {
        List<LibraryInfo> allLibraries = new ArrayList<>();

        Iterator<LibraryInfo> libraryInfoIterator = signatureDAO.getAllPossibleLibraries();

        while (libraryInfoIterator.hasNext()) {
            allLibraries.add(libraryInfoIterator.next());
        }

        if (allLibraries.size() < numLibraries) {
            throw new IllegalArgumentException("Not enough libraries in database");
        }

        Collections.shuffle(allLibraries, new Random());
        return allLibraries.subList(0, numLibraries);

    }
}
