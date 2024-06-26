package nl.tudelft.cornul11.thesis.corpus.database;

import com.zaxxer.hikari.HikariDataSource;
import nl.tudelft.cornul11.thesis.corpus.file.ClassFileInfo;
import nl.tudelft.cornul11.thesis.corpus.file.JarAndPomInfoExtractor;
import nl.tudelft.cornul11.thesis.corpus.model.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SignatureDAOImpl implements SignatureDAO {
    private final HikariDataSource ds;
    private static final Logger logger = LoggerFactory.getLogger(SignatureDAOImpl.class);
    private final long startTime = System.currentTimeMillis();
    private final String dbMode;

    public SignatureDAOImpl(HikariDataSource ds) {
        this.ds = ds;
        this.dbMode = "file";
    }

    public SignatureDAOImpl(HikariDataSource ds, String dbMode) {
        this.ds = ds;
        this.dbMode = Objects.requireNonNullElse(dbMode, "file");
    }

    @Override
    public int insertLibrary(JarAndPomInfoExtractor jarAndPomInfoExtractor, long jarHash, long jarCrc, boolean isBrokenJar, long jarCreationDate) {
        String insertLibraryQuery = "INSERT INTO libraries (group_id, artifact_id, version, jar_hash, jar_crc, is_uber_jar, total_class_files, disk_size, unique_signatures, creation_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        executeWithDeadlockRetry(connection -> {
            try (PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery,
                    Statement.RETURN_GENERATED_KEYS)) {
                libraryStatement.setString(1, jarAndPomInfoExtractor.getGroupId());
                libraryStatement.setString(2, jarAndPomInfoExtractor.getArtifactId());
                libraryStatement.setString(3, jarAndPomInfoExtractor.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setLong(5, jarCrc);
                libraryStatement.setBoolean(6, !isBrokenJar);
                // there won't be any matches with this lib because there is no signature in the
                // db, thus we don't need the total number of class files in it
                libraryStatement.setInt(7, -1); // total_class_files
                libraryStatement.setInt(8, 0); // disk_size
                libraryStatement.setInt(9, 0); // unique_signatures
                libraryStatement.setDate(10, new Date(jarCreationDate));
                libraryStatement.executeUpdate();

                logger.info("Library row inserted.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return 0;
    }

    @Override
    public int insertSignatures(List<Signature> signatures, long jarHash, long jarCrc, long jarCreationDate) {
        String insertLibraryQuery = "INSERT INTO libraries (group_id, artifact_id, version, jar_hash, jar_crc, is_uber_jar, total_class_files, disk_size, unique_signatures, creation_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String insertSignatureQuery = "INSERT INTO signatures (library_id, class_hash, class_crc) VALUES (?, ?, ?)"; // library_id is added here.

        AtomicInteger totalRowsInserted = new AtomicInteger();
        executeWithDeadlockRetry(connection -> {
            try {
                PreparedStatement libraryStatement = connection.prepareStatement(insertLibraryQuery,
                        Statement.RETURN_GENERATED_KEYS);
                Signature firstSignature = signatures.get(0);
                libraryStatement.setString(1, firstSignature.getGroupID());
                libraryStatement.setString(2, firstSignature.getArtifactId());
                libraryStatement.setString(3, firstSignature.getVersion());
                libraryStatement.setLong(4, jarHash);
                libraryStatement.setLong(5, jarCrc);
                libraryStatement.setBoolean(6, false);
                libraryStatement.setInt(7, signatures.size());
                libraryStatement.setInt(8, 0);
                libraryStatement.setInt(9, signatures.stream().map(Signature::getHash).collect(Collectors.toSet()).size());
                libraryStatement.setDate(10, new Date(jarCreationDate));
                libraryStatement.executeUpdate();

                ResultSet generatedKeys = libraryStatement.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int libraryId = generatedKeys.getInt(1);
                    PreparedStatement insertStatement = connection.prepareStatement(insertSignatureQuery);

                    // TODO: maybe switch to batch inserts here

                    int i = 0;
                    for (Signature signature : signatures) {
                        insertStatement.setInt(1, libraryId); // setting the library_id for each signature
                        insertStatement.setLong(2, signature.getHash());
                        insertStatement.setLong(3, signature.getCrc());
                        insertStatement.addBatch();

                        if (++i % 1000 == 0 || i == signatures.size()) {
                            int[] updatedRows = insertStatement.executeBatch();
                            for (int updatedRow : updatedRows) {
                                totalRowsInserted.addAndGet(updatedRow);
                            }
                        }

                        totalRowsInserted.getAndIncrement();
                    }
                }

                String rowsInserted = "\033[0;32m" + totalRowsInserted + "\033[0m";
                logger.info(rowsInserted + " signature row(s) inserted.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });

        return totalRowsInserted.get();
    }

    @Override
    public Iterator<nl.tudelft.cornul11.thesis.corpus.model.Dependency> getAllPossibleLibraries() {
        return new LibraryIterator(ds);
    }

    public class OracleLibrary {
        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public boolean isAnUberJar() {
            return isAnUberJar;
        }

        private final String groupId;
        private final String artifactId;
        private final String version;
        private final boolean isAnUberJar;

        public OracleLibrary(String groupId, String artifactId, String version, boolean isAnUberJar) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.isAnUberJar = isAnUberJar;
        }
    }

    @Override
    public boolean isLibraryInDB(String library) {
        String selectLibraryQuery = "SELECT 1 FROM libraries WHERE CONCAT(group_id, ':', artifact_id, ':', version) = ?";
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectLibraryQuery)) {
            statement.setString(1, library);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isLibraryInDBWithSignatures(String library) {
        String signaturesTable = Objects.equals(this.dbMode, "file") ? "signatures" : "signatures_memory";

        String selectLibraryQuery = "SELECT id FROM libraries WHERE CONCAT(group_id, ':', artifact_id, ':', version) = ? AND unique_signatures > 0";
        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectLibraryQuery)) {
            statement.setString(1, library);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    int libraryId = resultSet.getInt("id");

                    String selectSignaturesQuery = "SELECT 1 FROM " + signaturesTable + " WHERE library_id = ?";
                    try (PreparedStatement signatureStatement = connection.prepareStatement(selectSignaturesQuery)) {
                        signatureStatement.setInt(1, libraryId);
                        try (ResultSet signatureResultSet = signatureStatement.executeQuery()) {
                            return signatureResultSet.next();
                        }
                    }
                } else {
                    return false;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }



    public class LibraryCandidate {
        private Integer libraryId;
        private Set<Long> hashes;
        private Set<String> paths;
        private String groupId;
        private String artifactId;
        private String version;
        private boolean self;

        private Set<String> gas;
        private List<String> alternativeVersions;
        private List<LibraryCandidate> alternatives;
        private List<LibraryCandidate> includes;
        private List<LibraryCandidate> includedIn;
        private int expectedNumberOfClasses;
        private boolean perfectMatch;

        public Integer getLibraryId() {
            return libraryId;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        /**
         * Returns the AGV of the library.
         */
        public String getGAV() {
            return groupId + ":" + artifactId + ":" + version;
        }

        public boolean isSelf() {
            return self;
        }

        public LibraryCandidate setSelf(boolean self) {
            this.self = self;
            return this;
        }

        public boolean isDifferentVersion(LibraryCandidate other) {
            if (gas == null || other.artifactId == null || other.groupId == null) {
                return false;
            }
            return gas.contains(other.groupId + ":" + other.artifactId);
        }

        public int getExpectedNumberOfClasses() {
            return expectedNumberOfClasses;
        }

        public LibraryCandidate setGroupId(String groupId) {
            this.groupId = groupId;
            setGAS();
            return this;
        }

        private void setGAS() {
            if (groupId != null && artifactId != null) {
                if (gas == null) {
                    gas = new HashSet<>();
                }
                gas.add(groupId + ":" + artifactId);
            }
        }

        public LibraryCandidate setArtifactId(String artifactId) {
            this.artifactId = artifactId;
            setGAS();
            return this;
        }

        public LibraryCandidate setVersion(String version) {
            this.version = version;
            return this;
        }

        public LibraryCandidate setLibraryId(Integer libraryId) {
            this.libraryId = libraryId;
            return this;
        }

        public LibraryCandidate setHashes(Set<Long> hashes) {
            this.hashes = hashes;
            return this;
        }

        public LibraryCandidate setPaths(Set<String> paths) {
            this.paths = paths;
            return this;
        }

        public LibraryCandidate setPerfectMatch(boolean b) {
            this.perfectMatch = b;
            return this;
        }

        public boolean isPerfectMatch() {
            return perfectMatch;
        }

        public List<LibraryCandidate> getAlternatives() {
            return Objects.requireNonNullElseGet(this.alternatives, ArrayList::new);
        }

        public boolean addAlternative(LibraryCandidate alternative) {
            if (alternatives == null) {
                alternatives = new ArrayList<>();
            }
            alternatives.add(alternative);
            alternative.setGAS();
            setGAS();
            if (gas != null && alternative.gas != null) {
                gas.addAll(alternative.gas);
            }
            alternative.hashes = null;
            alternative.paths = null;
            return true;
        }

        public LibraryCandidate addIncludes(LibraryCandidate included) {
            if (this.includes == null) {
                this.includes = new ArrayList<>();
            }
            if (!this.includes.contains(included)) {
                this.includes.add(included);
                included.addIncludedIn(this);
            }
            return this;
        }

        public LibraryCandidate addIncludedIn(LibraryCandidate other) {
            if (this.includedIn == null) {
                this.includedIn = new ArrayList<>();
            }
            if (!this.includedIn.contains(other))
                this.includedIn.add(other);
            return this;
        }

        public boolean contains(LibraryCandidate candidate) {
            return this.hashes.size() >= candidate.hashes.size() && this.hashes.containsAll(candidate.hashes);
        }

        public boolean equals(LibraryCandidate other) {
            return this.libraryId.equals(other.libraryId)
                    || (this.getAlternatives().stream().anyMatch(other::equals));
        }

        public Set<Long> getHashes() {
            return this.hashes;
        }

        public LibraryCandidate setExpectedNumberOfTotalClasses(int nbUniqueLibClass) {
            this.expectedNumberOfClasses = nbUniqueLibClass;
            return this;
        }

        public double getIncludedRatio() {
            return this.getHashes().size() * 1.0 / this.expectedNumberOfClasses;
        }

        public String toJSON() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"id\": \"" + this.getGAV() + "\",");
            sb.append("\"ratio\": " + this.getIncludedRatio() + ",");
            sb.append("\"count\": " + this.getHashes().size() + ",");
            sb.append("\"total\": " + this.getExpectedNumberOfClasses() + ",");
            sb.append("\"self\": " + this.isSelf() + ",");
            sb.append("\"perfect\": " + this.perfectMatch + ",");
            sb.append("\"alternatives\": [");
            boolean isFirst = true;
            this.getAlternatives().sort((data1, data2) -> data1.getGAV().compareTo(data2.getGAV()));
            for (LibraryCandidate alternative : this.getAlternatives()) {
                if (!isFirst) {
                    sb.append(",");
                } else {
                    isFirst = false;
                }
                sb.append("\"" + alternative.getGAV() + "\"");
            }
            sb.append("],");
            sb.append("\"hashes\": [");
            if (this.hashes != null) {
                isFirst = true;
                for (Long hash : this.getHashes()) {
                    if (!isFirst) {
                        sb.append(",");
                    } else {
                        isFirst = false;
                    }
                    sb.append("\"" + hash + "\"");
                }
            }
            sb.append("],");
            sb.append("\"packages\": [");
            if (this.paths != null) {
                isFirst = true;
                for (String name : this.paths) {
                    if (!isFirst) {
                        sb.append(",");
                    } else {
                        isFirst = false;
                    }
                    sb.append("\"" + name + "\"");
                }
            }
            sb.append("],");
            sb.append("\"includedIn\": [");
            if (this.includedIn != null) {
                isFirst = true;
                this.includedIn.sort(Comparator.comparing(LibraryCandidate::getGAV));
                for (LibraryCandidate alternative : this.includedIn) {
                    if (!isFirst) {
                        sb.append(",");
                    } else {
                        isFirst = false;
                    }
                    sb.append("\"" + alternative.getGAV() + "\"");
                }
            }
            sb.append("],");
            sb.append("\"includes\": [");
            if (this.includes != null) {
                isFirst = true;
                this.includes.sort(Comparator.comparing(LibraryCandidate::getGAV));
                for (LibraryCandidate alternative : this.includes) {
                    if (!isFirst) {
                        sb.append(",");
                    } else {
                        isFirst = false;
                    }
                    sb.append("\"" + alternative.getGAV() + "\"");
                }
            }
            sb.append("]}");
            return sb.toString();
        }

        public List<String> getAlternativeVersions() {
            return alternativeVersions;
        }

        public void addAlternativeVersion(LibraryCandidate other) {
            if (this.alternativeVersions == null) {
                this.alternativeVersions = new ArrayList<>();
            }

            this.alternativeVersions.add(other.getGAV());
        }
    }

    @Override
    public List<LibraryCandidate> returnTopLibraryMatches(List<ClassFileInfo> signatures) {
        long startTime = System.currentTimeMillis();

        String createTempTable = "CREATE TEMPORARY TABLE IF NOT EXISTS temp_hashes (class_hash BIGINT NOT NULL)";
        String dropTempTable = "DROP TABLE temp_hashes";
        String insertIntoTempTable = "INSERT INTO temp_hashes (class_hash) VALUES (?)";

        String signaturesTable = Objects.equals(this.dbMode, "file") ? "signatures" : "signatures_memory";
        String classHashField = signaturesTable + ".class_hash";

        String mainQuery = String.format("SELECT temp_hashes.class_hash, library_id FROM %s " +
                "JOIN temp_hashes ON %s = temp_hashes.class_hash", signaturesTable, classHashField);

        List<LibraryCandidate> candidates = new ArrayList<>();

        Map<String, Set<Long>> packagesToHashes = new HashMap<>();
        Map<Long, Set<String>> hashToPackage = new HashMap<>();
        Set<Long> hashes = new HashSet<>();
        Set<String> paths = new HashSet<>();

        for (ClassFileInfo s : signatures) {
            hashes.add(s.getHashCode());
            String path = s.getClassName();

            boolean rootClass = path.lastIndexOf("/") == -1;

            if (rootClass) {
                continue;
            }

            String folder = path.substring(0, path.lastIndexOf("/"));
            // paths are unused for now
            paths.add(folder);

            // we want to also keep track of the packages that contain the same hash
            if (!packagesToHashes.containsKey(folder)) {
                packagesToHashes.put(folder, new HashSet<>());
            }

            // class hashes per package
            packagesToHashes.get(folder).add(s.getHashCode());

            // we want to keep track of the packages that contain the same hash
            if (!hashToPackage.containsKey(s.getHashCode())) {
                hashToPackage.put(s.getHashCode(), new HashSet<>());
            }

            // packages per class hash
            hashToPackage.get(s.getHashCode()).add(folder);
        }

        try (Connection connection = ds.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(createTempTable);
            }

            try (PreparedStatement statement = connection.prepareStatement(insertIntoTempTable)) {
                for (Long hash : hashes) {
                    statement.setLong(1, hash);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            int numUniqueHashes = hashes.size();
            Map<Long, Set<Integer>> hashToLib = new HashMap<>(numUniqueHashes);
            Map<Integer, Set<Long>> libToHash = new HashMap<>(100);
            Map<Integer, Set<String>> libToPackages = new HashMap<>(100);

            try (PreparedStatement statement = connection.prepareStatement(mainQuery)) {
                ResultSet resultSet = statement.executeQuery();
                while (resultSet.next()) {
                    Long classHash = resultSet.getLong("temp_hashes.class_hash");
                    int libraryId = resultSet.getInt("library_id");

                    // keep track of the hashes that are in many libraries
                    if (!hashToLib.containsKey(classHash)) {
                        hashToLib.put(classHash, new HashSet<>());
                    }
                    hashToLib.get(classHash).add(libraryId);

                    // keep track of the hashes contained in each library
                    if (!libToHash.containsKey(libraryId)) {
                        libToHash.put(libraryId, new HashSet<>());
                        libToPackages.put(libraryId, new HashSet<>());
                    }
                    libToHash.get(libraryId).add(classHash);
                    libToPackages.get(libraryId).addAll(hashToPackage.getOrDefault(classHash, Collections.emptySet()));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(dropTempTable);
            }
            // TODO: examine these files
            logger.info("# file not found in DB: " + (numUniqueHashes - hashToLib.size()) + " "
                    + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

            Set<LibraryCandidate> selfCandidates = new HashSet<>();
            lib:

            // for each detected library
            for (Integer lib : libToHash.keySet()) {
                Set<Long> hashesInLib = libToHash.get(lib); // get all of its hashes
                int numHashesInLib = hashesInLib.size();

                if (numHashesInLib < 2) { // if a lib has only one hash, we don't consider it
                    continue;
                }

                if (numHashesInLib * 1.0 / numUniqueHashes > 0.5) {
                    // if the lib includes more than 50% of the hashes in the input jar we consider it as a candidate
                    LibraryCandidate libraryCandidate = new LibraryCandidate()
                            .setLibraryId(lib)
                            .setHashes(hashesInLib);

                    // check if we matched itself
                    if (numHashesInLib * 1.0 / numUniqueHashes > 0.99) {
                        selfCandidates.add(libraryCandidate);
                        libraryCandidate.setSelf(true);
                    }
                    candidates.add(libraryCandidate);
                    continue;
                }
                for (String path : libToPackages.get(lib)) {
                    Set<Long> hashInPackage = packagesToHashes.get(path);
                    if (hashInPackage.size() == 1) {
                        continue;
                    }
                    // if the lib includes all the hashes of a package we do consider it
                    if (numHashesInLib >= hashInPackage.size() && hashesInLib.containsAll(hashInPackage)) {
                        candidates.add(new LibraryCandidate().setLibraryId(lib)
                                .setHashes(hashesInLib));
                        continue lib;
                    }
                }
            }

            logger.info("# Library Candidate: " + candidates.size() + "/" + libToHash.size() + " "
                    + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

            candidates.sort((data1, data2) -> data2.getLibraryId() - data1.getLibraryId());

            List<String> libIds = candidates.stream().map(entry -> "" + entry.getLibraryId())
                    .collect(Collectors.toList());
            try (PreparedStatement statement = connection
                    .prepareStatement("SELECT id, group_id, artifact_id, version, unique_signatures FROM libraries where id in ("
                            + String.join(", ", libIds) + ") ORDER BY id DESC")) {
                statement.execute();
                ResultSet result = statement.getResultSet();
                int index = 0;
                while (result.next()) {
                    int libraryId = result.getInt("id");
                    String resultGroupId = result.getString("group_id");
                    String resultArtifactId = result.getString("artifact_id");
                    String resultVersion = result.getString("version");
                    int numUniqueClasses = result.getInt("unique_signatures");

                    if (candidates.get(index).getLibraryId() != libraryId) {
                        throw new RuntimeException("The library id does not match.");
                    }
                    candidates.get(index).setGroupId(resultGroupId)
                            .setArtifactId(resultArtifactId)
                            .setVersion(resultVersion)
                            .setExpectedNumberOfTotalClasses(numUniqueClasses);
                    index++;
                }
            }
            logger.info("# Query for lib info: " + libIds.size() + " "
                    + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

            int nbAlternative = 0;

            ArrayList<LibraryCandidate> selfList = new ArrayList<>(selfCandidates);

            // Sort in decreasing order of count
            candidates.sort((data1, data2) -> {
                int compare = Double.compare(data2.getIncludedRatio(), data1.getIncludedRatio());
                if (compare == 0) {
                    compare = data2.getHashes().size() - data1.getHashes().size();
                    if (compare == 0) {
                        compare = data1.getExpectedNumberOfClasses() - data2.getExpectedNumberOfClasses();
                        if (compare == 0) {
                            compare = data1.getGAV().compareTo(data2.getGAV());
                        }
                    }
                }
                return compare;
            });

            int numTopCandidates = candidates.size();
            for (int i = 0; i < numTopCandidates; i++) {
                LibraryCandidate lib = candidates.get(i);
                if (lib.getHashes() == null) {
                    continue;
                }
                int libHashSize = lib.getHashes().size();
                for (LibraryCandidate self : selfList) {
                    if (lib.equals(self)) {
                        continue;
                    }
                    if (self.isDifferentVersion(lib)) {
                        lib.setSelf(true);
                        selfCandidates.add(lib);
                    }
                }
                for (int j = i; j < numTopCandidates; j++) {
                    LibraryCandidate lib2 = candidates.get(j);
                    if (lib.equals(lib2) || lib2.getHashes() == null) {
                        continue;
                    }
                    int lib2HashSize = lib2.getHashes().size();
                    // consider different version of the same lib as alternative
                    // list is sorted by best matches first
                    if (lib.isDifferentVersion(lib2) ||
                            // if the lib is included in another lib, we do consider it as alternative
                            (lib.expectedNumberOfClasses == lib2.expectedNumberOfClasses
                                    && libHashSize == lib2HashSize
                                    && lib.contains(lib2))) {
                        if ((lib.expectedNumberOfClasses == lib2.expectedNumberOfClasses
                                && libHashSize == lib2HashSize
                                && lib.contains(lib2))) {
                            lib.addAlternativeVersion(lib2);
                            logger.info("Adding alternative version to " + lib.getGAV() + ": " + lib2.getGAV());
                        }
                        lib.addAlternative(lib2);
                        nbAlternative++;
                    }
                }
            }
            logger.info("# Identify alternative: " + nbAlternative + " "
                    + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

            int numPerfectMatch = 0;
            // look for files that are only in one lib
            for (String pack : packagesToHashes.keySet()) {
                // identify all the lib of a package
                Set<Long> packHashes = packagesToHashes.get(pack);
                Set<Integer> libOfHash = new HashSet<>();
                for (Long hash : packHashes) {
                    Set<Integer> c = hashToLib.get(hash);
                    if (c != null)
                        libOfHash.addAll(c);
                }
                for (LibraryCandidate self : selfCandidates) {
                    libOfHash.remove(self.getLibraryId());
                    for (LibraryCandidate alternative : self.getAlternatives()) {
                        libOfHash.remove(alternative.getLibraryId());
                    }
                }
                if (libOfHash.isEmpty()) {
                    continue;
                }

                // check if the package is only in one lib
                for (LibraryCandidate lib : candidates) {
                    if (lib.getHashes() == null || lib.isSelf() || lib.isPerfectMatch()) {
                        continue;
                    }
                    if (lib.paths != null && !lib.paths.contains(pack)) {
                        continue;
                    }
                    Set<Integer> libAndAlternativeIds = new HashSet<>();
                    libAndAlternativeIds.add(lib.getLibraryId());
                    for (LibraryCandidate alternative : lib.getAlternatives()) {
                        libAndAlternativeIds.add(alternative.getLibraryId());
                    }
                    if (libOfHash.size() <= libAndAlternativeIds.size() &&
                            libAndAlternativeIds.containsAll(libOfHash)) {
                        lib.setPerfectMatch(true);
                        numPerfectMatch++;
                    }
                }
            }

            logger.info("# Identify perfect match: " + numPerfectMatch + " "
                    + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds.");

        } catch (SQLException e) {
            e.printStackTrace();
        }

        // ignore libraries that don't have any hashes, i.e., are alternatives
        List<LibraryCandidate> output = candidates.stream().filter(lib -> lib.getHashes() != null)
                .collect(Collectors.toList());

        long endTime = System.currentTimeMillis();
        logger.info("Top matches query took " + (endTime - startTime) / 1000.0 + " seconds.");
        return output;
    }

    private void executeWithDeadlockRetry(Consumer<Connection> action) {
        boolean success = false;
        while (!success) {
            try (Connection connection = ds.getConnection()) {
                connection.setAutoCommit(false);
                action.accept(connection);
                connection.commit();
                connection.setAutoCommit(true);
                success = true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1213) { // 1213 = ER_LOCK_DEADLOCK
                    handleDeadlock();
                } else {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private void handleDeadlock() {
        logger.error("Deadlock detected. Retrying...");
        try {
            // sleep for a random amount of time between 0.5 and 1 seconds
            Thread.sleep(500 + (int) (Math.random() * 500));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    public List<Long> getHashesForArtifactIdVersion(String artifactId, String version) {
        String mainQuery = "SELECT * FROM libraries l JOIN signatures s ON l.id = s.library_id " +
                "WHERE l.artifact_id = '" + artifactId + "' AND l.version = '" + version + "'";

        List<Long> libraryHashes = new ArrayList<>();

        try (Connection connection = ds.getConnection();
             PreparedStatement statement = connection.prepareStatement(mainQuery)) {

            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                long resultHash = resultSet.getLong("class_hash");
                libraryHashes.add(resultHash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return libraryHashes;
    }

    @Override
    public void closeConnection() {
        if (ds != null) {
            ds.close();
            logger.info("Total time spent in database: " + (System.currentTimeMillis() - startTime) / 1000 + " seconds.");
            logger.info("Database connection closed.");
        }
    }
}