package main.java.db;

import com.mongodb.client.*;
import main.java.City;
import main.java.fetch.FetchAdapter;
import org.bson.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MongoDBManager {
    private static MongoDBManager INSTANCE = new MongoDBManager();
    public static MongoDBManager getInstance() {
        return INSTANCE;
    }

    private final static String MONGO_URL = "mongodb://localhost:27017";
    private final static String DATABASE_NAME = "task2";

    private MongoClient mongoClient;
    private MongoDatabase database;

    private MongoDBManager() {
        mongoClient = MongoClients.create(MONGO_URL);
        database = mongoClient.getDatabase(DATABASE_NAME);
    }

    void close() {
        mongoClient.close();
    }

    public void resetLocationList() throws IOException {
        MongoCollection<Document> collection = database.getCollection("locations");
        collection.deleteMany(new Document());
        collection.insertMany(FetchAdapter.getInstance().fetchAllCities());
    }

    public ArrayList<City> getLocationList() {
        ArrayList<City> resultList = new ArrayList<>();
        MongoCollection<Document> collection = database.getCollection("locations");
        MongoCursor<Document> cursor = collection.find().iterator();
        try {
            while (cursor.hasNext()) {
                /*
                Location {
                    country: 'IT',
                    city: 'Pisa',
                    coordinates: {type:'point', coordinates: [43.716667, 10.400000]},
                    enabled: False
                    suggestedBy: ['1', '99']
                }
                 */
                Document bsonCity = cursor.next();
                String country = bsonCity.getString("country");
                String city = bsonCity.getString("city");

                Document bsonCoordinates = (Document)bsonCity.get("coordinates");
                List<Double> coords = (List<Double>)bsonCoordinates.get("coordinates");

                City newCity = new City(country, city, new City.Coords(coords.get(0), coords.get(0)));
                resultList.add(newCity);
            }
        } finally {
            cursor.close();
        }

        return resultList;
    }

    public static void main(String[] args) throws IOException {
        MongoDBManager.getInstance().resetLocationList();
        MongoDBManager.getInstance().getLocationList().forEach(c -> System.out.println(c.toString()));
    }
}
