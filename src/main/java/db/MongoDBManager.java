package main.java.db;

import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import main.java.City;
import main.java.User;
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

    public boolean registerUser(User user, String password) {
        MongoCollection<Document> collection = database.getCollection("users");
        Document userDoc = new Document("username", user.getUsername())
                .append("password", password)
                .append("name", user.getName())
                .append("surname", user.getSurname())
                .append("status", user.getStatus().ordinal());

        try {
            collection.insertOne(userDoc);
            return true;
        } catch(MongoWriteException e) {
            return false;
        }
    }

    public User getUserWithPassword(String username, String password) {
        MongoCollection<Document> collection = database.getCollection("users");
        MongoCursor<Document> cursor = collection.find(new Document("username", username)).cursor();
        if(!cursor.hasNext())
            return null; // Missing user

        Document userDoc = cursor.next();
        if(!userDoc.getString("password").equals(password))
            return null; // Invalid password

        User newUser = new User(username,
                userDoc.getString("name"),
                userDoc.getString("surname"),
                User.Status.values()[userDoc.getInteger("status")]);
        return newUser;
    }

    private void createUserIndex() {
        database.getCollection("users").createIndex(new Document("username", 1), new IndexOptions().unique(true));
    }

    public void dropAllCollections() {
        database.getCollection("users").drop();
        database.getCollection("locations").drop();
        database.getCollection("measurements").drop();
    }

    public static void main(String[] args) throws IOException {
        try {
            MongoDBManager.getInstance().dropAllCollections();

            // Reload locations
            MongoDBManager.getInstance().resetLocationList();
            MongoDBManager.getInstance().getLocationList().forEach(c -> System.out.println(c.toString()));

            // Reload users
            MongoDBManager.getInstance().createUserIndex();
            User adminUser = new User("admin", "NomeAdmin", "CognomeAdmin", User.Status.ADMIN);
            User neUser = new User("utente-s", "Utente", "Standard", User.Status.NOTENABLED);
            User eUser = new User("utente-e", "Utente", "Enabled", User.Status.ENABLED);

            // Test on users collection. TODO: should we divide import and testing code?
            boolean result;
            result = MongoDBManager.getInstance().registerUser(adminUser, "password");
            System.out.println("insert 1: " + ((result) ? "ok" : "not ok"));
            result = MongoDBManager.getInstance().registerUser(neUser, "password");
            System.out.println("insert 2: " + ((result) ? "ok" : "not ok"));
            result = MongoDBManager.getInstance().registerUser(eUser, "password");
            System.out.println("insert 3: " + ((result) ? "ok" : "not ok"));
            // try duplicate to ensure index is working
            result = MongoDBManager.getInstance().registerUser(eUser, "password");
            System.out.println("insert 4: " + ((!result) ? "ok" : "not ok"));

            // test login
            User resultUser;
            resultUser = MongoDBManager.getInstance().getUserWithPassword("tizio", "caio");
            System.out.println("check 1: " + ((resultUser == null) ? "ok" : "not ok"));
            resultUser = MongoDBManager.getInstance().getUserWithPassword("utente-e", "caio");
            System.out.println("check 2: " + ((resultUser == null) ? "ok" : "not ok"));
            resultUser = MongoDBManager.getInstance().getUserWithPassword("utente-e", "password");
            System.out.println("check 3: " + ((resultUser.equals(eUser)) ? "ok" : "not ok"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
