package main.java.db;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.IndexOptions;
import io.github.cbartosiak.bson.codecs.jsr310.duration.DurationAsDecimal128Codec;
import io.github.cbartosiak.bson.codecs.jsr310.localdate.LocalDateAsDateTimeCodec;
import io.github.cbartosiak.bson.codecs.jsr310.localdatetime.LocalDateTimeAsDateTimeCodec;
import main.java.City;
import main.java.User;
import main.java.fetch.FetchAdapter;
import main.java.fetch.FetchUtils;
import main.java.measures.MeasureValue;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.codecs.BsonTypeClassMap;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static com.mongodb.client.model.Accumulators.*;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;

public class MongoDBManager {
    // This is needed to switch default DATE_TIME decoding from Date to LocalDateTime
    public static final DocumentCodecProvider documentCodecProvider;
    static {
        Map<BsonType, Class<?>> replacements = new HashMap<>();
        replacements.put(BsonType.DATE_TIME, LocalDateTime.class);
        documentCodecProvider = new DocumentCodecProvider(new BsonTypeClassMap(replacements));
    }

    public final static CodecRegistry CODEC_REGISTRY = CodecRegistries.fromRegistries(
            CodecRegistries.fromProviders(documentCodecProvider),
            CodecRegistries.fromCodecs(new DurationAsDecimal128Codec()),
            CodecRegistries.fromCodecs(new LocalDateAsDateTimeCodec()),
            CodecRegistries.fromCodecs(new LocalDateTimeAsDateTimeCodec()),
            MongoClientSettings.getDefaultCodecRegistry()
    );

    private final static String MONGO_URL = "mongodb://localhost:27017";
    private final static String DATABASE_NAME = "task2";

    private static MongoDBManager INSTANCE = new MongoDBManager();
    public static MongoDBManager getInstance() {
        return INSTANCE;
    }

    private MongoClient mongoClient;
    private MongoDatabase database;

    private MongoDBManager() {
        mongoClient = MongoClients.create(MONGO_URL);
        database = mongoClient.getDatabase(DATABASE_NAME).withCodecRegistry(CODEC_REGISTRY);
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

                City newCity = new City(country, city, new City.Coords(coords.get(0), coords.get(1)));
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

    public ArrayList<User> getUsersByStatus(int status ) {
        MongoCollection<Document> collection = database.getCollection("users");
        MongoCursor<Document> cursor = collection.find().cursor();
        if(!cursor.hasNext())
            return null; // No Users

        ArrayList<User> userList = new ArrayList<>();
        while(cursor.hasNext()) {
            Document userDoc = cursor.next();
            if (userDoc.getInteger("status").equals(status)) {
                User newUser = new User(userDoc.getString("username"),
                        userDoc.getString("name"),
                        userDoc.getString("surname"),
                        User.Status.values()[userDoc.getInteger("status")]);
                userList.add(newUser);
            }
        }
        return userList;

    }

    private void createUserIndex() {
        database.getCollection("users").createIndex(new Document("username", 1), new IndexOptions().unique(true));
    }

    public void loadPollutionFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        MongoCollection<Document> collection = database.getCollection("measurespoll");
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1))
            FetchAdapter.getInstance().fetchPollutionData(collection, city, d);
    }

    public void loadPastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        MongoCollection<Document> collection = database.getCollection("measureswpast");
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1))
            FetchAdapter.getInstance().fetchHistoricalData(collection, city, d);
    }

    public void testMeasureImport(City city) throws IOException {
        for(int i=0; i<15; i++) {
            FetchAdapter.getInstance()
                    .fetchPollutionData(database.getCollection("measurespoll"), city, LocalDate.now().minusDays(5 + i));
            FetchAdapter.getInstance()
                    .fetchHistoricalData(database.getCollection("measureswpast"), city, LocalDate.now().minusDays(5 + i));
        }

        FetchAdapter.getInstance().fetchForecastData(database.getCollection("measureswfor"), city);
    }

    private HashMap<City.CityName, ArrayList<MeasureValue>> parsePollutionList(
            AggregateIterable<Document> aggregateList, String arrayName) {
        HashMap<City.CityName, ArrayList<MeasureValue>> cityMap = new HashMap<>();

        for(Document d : aggregateList) { // iterate by city first
            City.CityName city = new City.CityName(d.getString("country"), d.getString("city"));

            // iterate dates (day only)
            List<Document> daylist = d.getList(arrayName, Document.class);
            for(Document dd : daylist) {
                LocalDateTime day = dd.get("datetime", LocalDateTime.class);
                // iterate pollutants
                List<Document> pollutants = dd.getList("pollutants", Document.class);
                for(Document ddd : pollutants) {
                    MeasureValue m = new MeasureValue(day, city,
                            ddd.getString("pollutant"),
                            ddd.getDouble("value"),
                            ddd.getString("unit"));

                    if(!cityMap.containsKey(city))
                        cityMap.put(city, new ArrayList<>());
                    cityMap.get(city).add(m);
                }
            }
        }

        return cityMap;
    }

    public HashSet<LocalDate> getPollutionAvailableDates(City city) {
        List<Bson> pipeline = Arrays.asList(
                match(and(eq("city", city.getCity()), eq("country", city.getCountry()),
                        eq("enabled", true))),
                unwind("$pollutionMeasurements"),
                project(fields(excludeId(), computed("year", eq("$year", "$pollutionMeasurements.datetime")),
                        computed("month", eq("$month", "$pollutionMeasurements.datetime")),
                        computed("day", eq("$dayOfMonth", "$pollutionMeasurements.datetime")))),
                group(and(eq("year", "$year"), eq("month", "$month"), eq("day", "$day")),
                        sum("count", 1L)),
                project(fields(excludeId(), computed("date", eq("$dateFromParts",
                        and(eq("year", "$_id.year"), eq("month", "$_id.month"),
                                eq("day", "$_id.day")))))));

        HashSet<LocalDate> resultSet = new HashSet<>();

        MongoCollection<Document> collection = database.getCollection("measurespoll");
        AggregateIterable<Document> aggregateIterable = collection.aggregate(pipeline);
        for(Document d : aggregateIterable)
            resultSet.add(d.get("date", LocalDateTime.class).toLocalDate());

        return resultSet;
    }

    // TODO: hours are not discretized
    public HashMap<City.CityName, ArrayList<MeasureValue>> getHourlyPollution(LocalDateTime startDate, LocalDateTime endDate) {
        if(startDate.compareTo(endDate) > 0)
            return null;

        MongoCollection<Document> collection = database.getCollection("measurespoll");

        List<Bson> pipeline = Arrays.asList(
                    match(and(lte("periodStart", endDate), gte("periodEnd", startDate),
                        eq("enabled", true))),
                    unwind("$pollutionMeasurements"),
                    match(and(lte("pollutionMeasurements.datetime", endDate),
                            gte("pollutionMeasurements.datetime", startDate))),
                    unwind("$pollutionMeasurements.measurements"),
                    project(fields(include("city", "country", "coordinates"), excludeId(),
                            computed("pollutionMeasurements.year", eq("$year", "$pollutionMeasurements.datetime")),
                            computed("pollutionMeasurements.month", eq("$month", "$pollutionMeasurements.datetime")),
                            computed("pollutionMeasurements.day", eq("$dayOfMonth", "$pollutionMeasurements.datetime")),
                            computed("pollutionMeasurements.hour", eq("$hour", "$pollutionMeasurements.datetime")),
                            computed("measurement", "$pollutionMeasurements.measurements"))),
                    group(and(eq("city", "$city"), eq("country", "$country"),
                            eq("year", "$pollutionMeasurements.year"), eq("month", "$pollutionMeasurements.month"),
                            eq("day", "$pollutionMeasurements.day"), eq("hour", "$pollutionMeasurements.hour"),
                            eq("pollutant", "$measurement.name"), eq("unit", "$measurement.unit")),
                            avg("value", "$measurement.value")),
                    group(and(eq("city", "$_id.city"), eq("country", "$_id.country"),
                            eq("datetime", eq("$dateFromParts", and(eq("year", "$_id.year"),
                                    eq("month", "$_id.month"), eq("day", "$_id.day"),
                                    eq("hour", "$_id.hour"))))),
                            push("measurements", and(eq("pollutant", "$_id.pollutant"),
                                    eq("unit", "$_id.unit"), eq("value", "$value")))),
                    group(and(eq("city", "$_id.city"), eq("country", "$_id.country")),
                            push("hourlymeasurements", and(eq("datetime", "$_id.datetime"),
                                    eq("pollutants", "$measurements")))),
                    project(fields(excludeId(), computed("city", "$_id.city"),
                            computed("country", "$_id.country"), include("hourlymeasurements"))));

        AggregateIterable<Document> aggregateList = collection.aggregate(pipeline);
        return parsePollutionList(aggregateList, "hourlymeasurements");
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPollution(LocalDate startDate, LocalDate endDate) {
        return getDailyPollution(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPollution(LocalDateTime startDate, LocalDateTime endDate) {
        if(startDate.compareTo(endDate) > 0)
            return null;

        MongoCollection<Document> collection = database.getCollection("measurespoll");

        List<Bson> pipeline = Arrays.asList(match(and(lt("periodStart",
                endDate), gte("periodEnd",
                startDate), eq("enabled", true))),
                unwind("$pollutionMeasurements"),
                match(and(lte("pollutionMeasurements.datetime", endDate),
                        gte("pollutionMeasurements.datetime", startDate))),
                unwind("$pollutionMeasurements.measurements"),
                project(fields(include("city", "country", "coordinates"), excludeId(),
                        computed("pollutionMeasurements.year", eq("$year", "$pollutionMeasurements.datetime")),
                        computed("pollutionMeasurements.month", eq("$month", "$pollutionMeasurements.datetime")),
                        computed("pollutionMeasurements.day", eq("$dayOfMonth", "$pollutionMeasurements.datetime")),
                        computed("pollutionMeasurements.hour", eq("$hour", "$pollutionMeasurements.datetime")),
                        computed("measurement", "$pollutionMeasurements.measurements"))),
                group(and(eq("city", "$city"), eq("country", "$country"),
                        eq("year", "$pollutionMeasurements.year"), eq("month", "$pollutionMeasurements.month"),
                        eq("day", "$pollutionMeasurements.day"), eq("pollutant", "$measurement.name"),
                        eq("unit", "$measurement.unit")), avg("value", "$measurement.value")),
                group(and(eq("city", "$_id.city"), eq("country", "$_id.country"),
                        eq("datetime", eq("$dateFromParts",and(eq("year", "$_id.year"),
                                eq("month", "$_id.month"), eq("day", "$_id.day"))))),
                        push("pollutants", and(eq("pollutant", "$_id.pollutant"),
                                eq("unit", "$_id.unit"), eq("value", "$value")))),
                group(and(eq("city", "$_id.city"), eq("country", "$_id.country")),
                        push("dailymeasurements", and(eq("datetime", "$_id.datetime"),
                                eq("pollutants", "$pollutants")))),
                project(fields(excludeId(), computed("city", "$_id.city"),
                        computed("country", "$_id.country"), include("dailymeasurements"))));

        AggregateIterable<Document> aggregateList = collection.aggregate(pipeline);
        return parsePollutionList(aggregateList, "dailymeasurements");
    }

    public void loadMeasure(LocalDateTime startDate, LocalDateTime endDate ) {

    }

    public void dropAllCollections() {
        database.getCollection("users").drop();
        database.getCollection("locations").drop();
        database.getCollection("measurespoll").drop();
        database.getCollection("measureswfor").drop();
        database.getCollection("measureswpast").drop();
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

            // try loading pollution measures
            City cityRome = new City("IT", "Roma", new City.Coords(41.902782, 12.4963));
            MongoDBManager.getInstance().testMeasureImport(cityRome);

            // fetch pollution by hours and days
            System.out.println("Test getHourlyPollution:");
            HashMap<City.CityName, ArrayList<MeasureValue>> mres;
            mres = MongoDBManager.getInstance().getHourlyPollution(LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(5));
            for(MeasureValue m : mres.get(cityRome.getCityName()))
                System.out.println(m.toString());
            System.out.println("Test getDailyPollution:");
            mres = MongoDBManager.getInstance().getDailyPollution(LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(5));
            for(MeasureValue m : mres.get(cityRome.getCityName()))
                System.out.println(m.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
