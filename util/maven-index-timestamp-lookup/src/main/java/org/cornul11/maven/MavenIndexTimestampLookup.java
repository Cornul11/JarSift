/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.cornul11.maven;


import com.google.inject.Guice;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.eclipse.sisu.launch.Main;
import org.eclipse.sisu.space.BeanScanning;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static spark.Spark.get;
import static spark.Spark.port;

/**
 * Collection of some use cases.
 */
@Singleton
@Named
public class MavenIndexTimestampLookup {
    private final Indexer indexer;

    private final IndexUpdater indexUpdater;

    private final Map<String, IndexCreator> indexCreators;

    public static void main(String[] args) throws IOException {
        final com.google.inject.Module app = Main.wire(BeanScanning.INDEX);
        MavenIndexTimestampLookup mavenIndexTimestampLookup = Guice.createInjector(app).getInstance(MavenIndexTimestampLookup.class);

        if (args.length > 0 && "server".equals(args[0])) {
            mavenIndexTimestampLookup.updateLocalIndex();

            IndexingContext centralIndex = mavenIndexTimestampLookup.createCentralContext();
            mavenIndexTimestampLookup.startServer(centralIndex);
        } else {
            mavenIndexTimestampLookup.perform(args);
        }
    }

    @Inject
    public MavenIndexTimestampLookup(Indexer indexer, IndexUpdater indexUpdater, Map<String, IndexCreator> indexCreators) {
        this.indexer = requireNonNull(indexer);
        this.indexUpdater = requireNonNull(indexUpdater);
        this.indexCreators = requireNonNull(indexCreators);
    }

    private IndexingContext createCentralContext() throws IOException {
        // Files where local cache is (if any) and Lucene Index should be located
        File centralLocalCache = new File("target/central-cache");
        File centralIndexDir = new File("target/central-index");

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<>();
        indexers.add(requireNonNull(indexCreators.get("min")));
        indexers.add(requireNonNull(indexCreators.get("jarContent")));
        indexers.add(requireNonNull(indexCreators.get("maven-plugin")));

        // Create context for central repository index
        return indexer.createIndexingContext(
                "central-context",
                "central",
                centralLocalCache,
                centralIndexDir,
                "https://repo1.maven.org/maven2",
                null,
                true,
                true,
                indexers);
    }

    private String lookupArtifactLastModified(IndexingContext centralContext, String groupId, String artifactId, String version) throws IOException {
        // construct the query for known GA
        Query query = constructQuery(groupId, artifactId, version);

        IteratorSearchRequest request = new IteratorSearchRequest(query, Collections.singletonList(centralContext));
        IteratorSearchResponse response = indexer.searchIterator(request);

        if (response.getTotalHitsCount() > 0) {
            ArtifactInfo ai = response.iterator().next();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            return sdf.format(new Date(ai.getLastModified()));
        } else {
            return "Artifact not found";
        }
    }


    private Query constructQuery(String groupId, String artifactId, String version) {
        // construct the query for known GA
        final Query groupIdQ = indexer.constructQuery(MAVEN.GROUP_ID, new SourcedSearchExpression(groupId));
        final Query artifactIdQ = indexer.constructQuery(MAVEN.ARTIFACT_ID, new SourcedSearchExpression(artifactId));
        final Query versionQ = indexer.constructQuery(MAVEN.VERSION, new SourcedSearchExpression(version));

        return new BooleanQuery.Builder()
                .add(groupIdQ, Occur.MUST)
                .add(artifactIdQ, Occur.MUST)
                .add(versionQ, Occur.MUST)
                // we want "jar" artifacts only
//                .add(indexer.constructQuery(MAVEN.PACKAGING, new SourcedSearchExpression("jar")), Occur.MUST)
//                 we want main artifacts only (no classifier)
//                 Note: this below is unfinished API, needs fixing
//                .add(indexer.constructQuery(MAVEN.CLASSIFIER, new SourcedSearchExpression(Field.NOT_PRESENT)), Occur.MUST_NOT)
                .build();
    }

    public void perform(String[] args) throws IOException {
        IndexingContext centralContext = createCentralContext();
        try {
            String groupId = args[0];
            String artifactId = args[1];
            String version = args[2];
            Query query = constructQuery(groupId, artifactId, version);
            executeQuery(centralContext, query);
        } finally {
            indexer.closeIndexingContext(centralContext, false);
        }
    }

    private void executeQuery(IndexingContext context, Query query) throws IOException {
        IteratorSearchRequest request = new IteratorSearchRequest(query, Collections.singletonList(context));
        IteratorSearchResponse response = indexer.searchIterator(request);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        System.out.println("Found " + response.getTotalHitsCount() + " artifacts.");
        for (ArtifactInfo ai : response) {
            System.out.println("GAV: " + ai.getGroupId() + ":" + ai.getArtifactId() + ":" + ai.getVersion());
            System.out.println("Last Modified: " + sdf.format(new Date(ai.getLastModified())));
        }
    }

    private void startServer(IndexingContext centralIndex) {
        port(8032);

        get("/lookup", (request, response) -> {
            String groupId = request.queryParams("groupId");
            String artifactId = request.queryParams("artifactId");
            String version = request.queryParams("version");

            if (groupId == null || artifactId == null || version == null) {
                response.status(400);
                return "Missing query parameters";
            }

            try {
                String lastModified = lookupArtifactLastModified(centralIndex, groupId, artifactId, version);
                if (!"Artifact not found".equals(lastModified)) {
                    return lastModified;
                } else {
                    response.status(404);
                    return "Artifact not found";
                }
            } catch (Exception e) {
                response.status(500);
                e.printStackTrace();
                return "Internal server error: " + e.getMessage();
            }
        });

        // when the server is stopped, close the indexer
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutting down...");
                indexer.closeIndexingContext(centralIndex, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    private void updateLocalIndex() throws IOException {
        Instant updateStart = Instant.now();
        System.out.println("Updating index...");
        System.out.println("This might take a while on first run, so please be patient!");

        IndexingContext centralContext = createCentralContext();

        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, new Java11HttpClient());
        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
        if (updateResult.isFullUpdate()) {
            System.out.println("Full update happened!");
        } else if (updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
            System.out.println("No update needed, index is up to date!");
        } else {
            System.out.println("Incremental update happened, change covered " + centralContextCurrentTimestamp
                    + " - " + updateResult.getTimestamp() + " period.");
        }

        indexer.closeIndexingContext(centralContext, false);
        System.out.println("Finished in "
                + Duration.between(updateStart, Instant.now()).getSeconds() + " sec");
        System.out.println();
    }

    private static class Java11HttpClient implements ResourceFetcher {
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        private URI uri;

        @Override
        public void connect(String id, String url) {
            this.uri = URI.create(url + "/");
        }

        @Override
        public void disconnect() {
        }

        @Override
        public InputStream retrieve(String name) throws IOException {
            HttpRequest request =
                    HttpRequest.newBuilder().uri(uri.resolve(name)).GET().build();
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                    return response.body();
                } else {
                    throw new IOException("Unexpected response: " + response);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
    }
}
