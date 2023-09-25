package nl.tudelft.cornul11.thesis.corpus.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import nl.tudelft.cornul11.thesis.corpus.util.ConfigurationLoader;
import org.bson.Document;

import java.util.ArrayList;

public class MongoDbClient {
    private final MongoClient mongoClient;
    private final ConfigurationLoader config;

    public MongoDbClient(ConfigurationLoader config) {
        this.mongoClient = MongoClients.create(config.getMongoDbConnectionString());
        this.config = config;
    }

    public ArrayList<Document> getVulnerabilities(String library, String version) {
        MongoDatabase database = mongoClient.getDatabase(config.getMongoDbDatabase());
        MongoCollection<Document> collection = database.getCollection(config.getMongoDbCollection());

        return collection.find(Filters.and(
                Filters.eq("affected.package.name", library),
                Filters.eq("affected.package.ecosystem", "Maven"),
                Filters.in("affected.versions", version))
        ).into(new ArrayList<>());
    }
}
