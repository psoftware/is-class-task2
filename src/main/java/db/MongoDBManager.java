package main.java.db;

import com.mongodb.client.*;
import org.bson.Document;

public class MongoDBManager {
    private static MongoDBManager INSTANCE = new MongoDBManager();
    public static MongoDBManager getInstance() {
        return INSTANCE;
    }

    private final static String DATABASE_NAME = "mydb";
    private MongoClient mongoClient;
    private MongoDatabase database;

    private MongoDBManager() {
        mongoClient = MongoClients.create("mongodb://localhost:27017");
        database = mongoClient.getDatabase(DATABASE_NAME);
        MongoCollection<Document> collection = database.getCollection("test");
    }

    void close() {
        mongoClient.close();
    }
}
