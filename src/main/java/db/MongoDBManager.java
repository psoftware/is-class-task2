package main.java.db;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.UpdateResult;
import io.github.cbartosiak.bson.codecs.jsr310.duration.DurationAsDecimal128Codec;
import io.github.cbartosiak.bson.codecs.jsr310.localdate.LocalDateAsDateTimeCodec;
import io.github.cbartosiak.bson.codecs.jsr310.localdatetime.LocalDateTimeAsDateTimeCodec;
import main.java.City;
import main.java.User;
import main.java.fetch.FetchAdapter;
import main.java.fetch.FetchUtils;
import main.java.gui.ProgressHandler;
import main.java.measures.MeasureValue;
import org.bson.BsonNull;
import org.bson.BsonType;
import org.bson.Document;
import org.bson.codecs.BsonTypeClassMap;
import org.bson.codecs.DocumentCodecProvider;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;
import org.json.JSONObject;

import javax.print.Doc;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

import static com.mongodb.client.model.Accumulators.*;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.Sorts.descending;



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

    private final String MONGO_URL =
            SettingsManager.MAINSETTINGS.getOrSetDefault("mongodb", "url", "mongodb://localhost:27017");
    //private final static String MONGO_URL = "mongodb://replica1:27017,replica2:27017,replica3:27017/?replicaSet=rs0";

    private final static String DATABASE_NAME = "task2";

    enum AppCollection {
        LOCATIONS("locations", ReadConcern.LOCAL, WriteConcern.W2.withJournal(true), ReadPreference.nearest()),
        PAST_WEATHER("measureswpast", ReadConcern.LOCAL, WriteConcern.W2.withJournal(true), ReadPreference.nearest()),
        FORECAST_WEATHER("measureswfor", ReadConcern.LOCAL, WriteConcern.W2.withJournal(true), ReadPreference.nearest()),
        POLLUTION("measurespoll", ReadConcern.LOCAL, WriteConcern.W2.withJournal(true), ReadPreference.nearest()),
        USERS("users", ReadConcern.MAJORITY, WriteConcern.MAJORITY.withJournal(false), ReadPreference.nearest());

        private final String name;
        private final ReadConcern rc;
        private final WriteConcern wc;

        public ReadConcern getReadConcern() { return rc; }
        public WriteConcern getWriteConcern() { return wc; }
        public ReadPreference getReadPreference() { return rp; }

        private final ReadPreference rp;
        AppCollection(String name) { this(name, null, null, null); }
        AppCollection(String name, ReadConcern rc, WriteConcern wc, ReadPreference rp) {
            this.name = name;
            this.rc = rc;
            this.wc = wc;
            this.rp = rp;
        }

        public String getName() { return name; }

        public MongoCollection<Document> get(MongoDatabase d) {
            MongoCollection<Document> collection = d.getCollection(this.name);
            if(rc != null)
                collection = collection.withReadConcern(rc);
            if(wc != null)
                collection = collection.withWriteConcern(wc);
            if(rp != null)
                collection = collection.withReadPreference(rp);
            return collection;
        }
    };

    private static MongoDBManager INSTANCE = new MongoDBManager();
    public static MongoDBManager getInstance() {
        return INSTANCE;
    }

    private MongoClient mongoClient;
    protected MongoDatabase database;

    private MongoDBManager() {
        mongoClient = MongoClients.create(MONGO_URL);
        database = mongoClient.getDatabase(DATABASE_NAME).withCodecRegistry(CODEC_REGISTRY);
    }

    void close() {
        mongoClient.close();
    }

    /**
     * Merge OpenAQ Location list to locations collection
     * @throws IOException due to OpenAQ exceptions
     */
    public void syncLocationList() throws IOException {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        try {
            // duplicate check is made by unique compound index on (country, city), skip duplicate errors using ordered:false option
            collection.insertMany(FetchAdapter.getInstance().fetchAllCities(), new InsertManyOptions().ordered(false));
        } catch (MongoBulkWriteException e) {
            // duplicates cause a MongoBulkWriteException, we should skip this
            //return e.getWriteResult().getInsertedCount(); // TODO: return updated locations and implement this on GUI
        }
    }

    /**
     * Wipe out locations collection and reload it from OpenAQ
     * @throws IOException due to OpenAQ exceptions
     */
    public void resetLocationList() throws IOException {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        collection.deleteMany(new Document());
        collection.insertMany(FetchAdapter.getInstance().fetchAllCities());
    }

    public ArrayList<City> getLocationList(User.Status userStatus) {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        MongoCursor<Document> cursor = null;
        if(userStatus == User.Status.NOTENABLED)
            cursor = collection.find(new Document("enabled", true)).iterator();
        else
            cursor = collection.find().iterator();

        return parseLocations(cursor);
    }

    /**
     * Search locations by country and city. Performs an exact case insensitive search
     * @param userStatus filter based on enabled status
     * @param country if empty, don't filter by country
     * @param city if empty, don't filter by city
     * @return list of found locations
     */
    public ArrayList<City> searchLocation(User.Status userStatus, String country, String city) {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);

        Document query = new Document();
        if(!country.equals(""))
            query.append("country", country);
        if(!city.equals(""))
            query.append("city", city);
        if(userStatus == User.Status.NOTENABLED)
            query.append("enabled", true);

        MongoCursor<Document> cursor = collection.find(query).collation(Collation.builder().locale("en")
                .collationStrength(CollationStrength.SECONDARY).build()).cursor();

        return parseLocations(cursor);
    }

    private ArrayList<City> parseLocations(MongoCursor<Document> cursor) {
        ArrayList<City> resultList = new ArrayList<>();
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
                boolean enabled = bsonCity.getBoolean("enabled");

                Document bsonCoordinates = (Document)bsonCity.get("coordinates");
                List<Double> coords = (List<Double>)bsonCoordinates.get("coordinates");

                City newCity = new City(country, city, enabled, new City.Coords(coords.get(0), coords.get(1)));
                resultList.add(newCity);
            }
        } finally {
            cursor.close();
        }

        return resultList;
    }

    /**
     * Get Top N locations by vote count
     * @param n location count limit
     * @return list of locations with vote count field initialized
     */
    public ArrayList<City> getTopLocationsByVoteCount(int n) {
        ArrayList<City> resultList = new ArrayList<>();
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        MongoCursor<Document> cursor = collection.aggregate(Arrays.asList(
                match(eq("enabled", false)),
                project(fields(include("country", "city", "coordinates", "enabled"),
                        computed("votecount", new Document("$size", "$votes")))),
                sort(descending("votecount")), limit(n))).cursor();

        try {
            while (cursor.hasNext()) {
                /*Location {
                    country: 'IT',
                    city: 'Pisa',
                    coordinates: {type:'point', coordinates: [43.716667, 10.400000]},
                    enabled: False
                    votecount : 10
                }*/
                Document bsonCity = cursor.next();
                String country = bsonCity.getString("country");
                String city = bsonCity.getString("city");
                boolean enabled = bsonCity.getBoolean("enabled");

                Document bsonCoordinates = (Document)bsonCity.get("coordinates");
                List<Double> coords = (List<Double>)bsonCoordinates.get("coordinates");

                Integer voteCount = bsonCity.getInteger("votecount");

                City newCity = new City(country, city, enabled, new City.Coords(coords.get(0), coords.get(1)), voteCount);
                resultList.add(newCity);
            }
        } finally { cursor.close(); }

        return resultList;
    }

    public boolean registerUser(User user, String password) {
        MongoCollection<Document> collection = AppCollection.USERS.get(database);
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
        MongoCollection<Document> collection = AppCollection.USERS.get(database);
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

    public ArrayList<User> getUsersByStatus(User.Status status, PageCursor<User> pageCursor) {
        return getUsersByStatus(new User.Status[]{status}, pageCursor);
    }

    public ArrayList<User> getUsersByStatus(User.Status[] ORstatus, PageCursor<User> pageCursor) {
        MongoCollection<Document> collection = AppCollection.USERS.get(database);

        Integer[] inArray = new Integer[ORstatus.length]; int i=0;
        for(User.Status status : ORstatus)
            inArray[i++] = status.ordinal();

        Document filter = new Document("status", new Document("$in", Arrays.asList(inArray)));
        FindIterable<Document> queryIterable = collection.find(filter);
        if(pageCursor != null)
            queryIterable = queryIterable.skip(pageCursor.getPage()*pageCursor.getDocPerPage())
                    .limit(pageCursor.getDocPerPage());

        MongoCursor<Document> cursor = queryIterable.iterator();

        ArrayList<User> userList = new ArrayList<>();
        while(cursor.hasNext()) {
            Document userDoc = cursor.next();
            User newUser = new User(userDoc.getString("username"),
                    userDoc.getString("name"),
                    userDoc.getString("surname"),
                    User.Status.values()[userDoc.getInteger("status")]);
            userList.add(newUser);
        }
        return userList;

    }

    // single text unique index on username
    protected void createUserIndex() {
        database.getCollection(AppCollection.USERS.getName()).createIndex(new Document("username", 1), new IndexOptions().unique(true));
    }

    protected void dropUserIndex() {
        database.getCollection(AppCollection.USERS.getName()).dropIndex(new Document("username", 1));
    }

    // compound unique index on (country, city)
    protected void createLocationIndex() {
        database.getCollection(AppCollection.LOCATIONS.getName()).createIndex(new Document("country", 1).append("city", 1),
                new IndexOptions().unique(true).name("city_country_unique"));

        database.getCollection(AppCollection.LOCATIONS.getName()).createIndex(new Document("country", 1).append("city", 1),
                new IndexOptions().unique(false).name("city_country_en_collation2")
                        .collation(Collation.builder().locale("en").collationStrength(CollationStrength.SECONDARY).build()));
    }

    protected void dropLocationIndex() {
        database.getCollection(AppCollection.LOCATIONS.getName()).dropIndex("city_country_unique");
        database.getCollection(AppCollection.LOCATIONS.getName()).dropIndex("city_country_en_collation2");
    }

    public void loadPollutionFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        loadPollutionFromAPI(city, startDate, endDate, null);
    }

    public boolean isMeasureCollection(AppCollection appCollection) {
        return appCollection == AppCollection.PAST_WEATHER || appCollection == AppCollection.FORECAST_WEATHER
                || appCollection == AppCollection.POLLUTION;
    }

    protected void createMeasuresIndex(AppCollection appCollection) {
        if(!isMeasureCollection(appCollection))
            throw new IllegalArgumentException("appCollection is not a measure collection!");

        database.getCollection(appCollection.getName()).createIndex(
                new Document("country", 1).append("city", 2).append("periodStart", 1).append("periodEnd", 1));
    }

    protected void dropMeasuresIndex(AppCollection appCollection) {
        if(!isMeasureCollection(appCollection))
            throw new IllegalArgumentException("appCollection is not a measure collection!");

        database.getCollection(appCollection.getName()).dropIndex(
                new Document("country", 1).append("city", 2).append("periodStart", 1).append("periodEnd", 1));
    }


    public void loadPollutionFromAPI(City city, LocalDate startDate, LocalDate endDate, ProgressHandler progress) throws IOException {
        if(progress != null) progress.setMaxProgress((int)ChronoUnit.DAYS.between(startDate, endDate));

        MongoCollection<Document> collection = AppCollection.POLLUTION.get(database);

        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1)) {
            List<Document> pollutionData = FetchAdapter.getInstance().fetchPollutionData(city, d);
            addDataToCollection(collection, city, d, "pollutionMeasurements", pollutionData);
            if(progress != null) progress.increaseProgress();
        }
    }

    public void loadPastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        loadPastWeatherFromAPI(city, startDate, endDate, null);
    }

    public void loadPastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate, ProgressHandler progress) throws IOException {
        if(startDate.isAfter(LocalDate.now()) || endDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Cannot fetch past weather for future days");

        if(progress != null) progress.setMaxProgress((int)ChronoUnit.DAYS.between(startDate, endDate));

        MongoCollection<Document> collection = AppCollection.PAST_WEATHER.get(database);
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1)) {
            List<Document> mongoHourlyList = FetchAdapter.getInstance().fetchHistoricalData(city, d);
            addDataToCollection(collection, city, d, "weatherCondition", mongoHourlyList);
            if(progress != null) progress.increaseProgress();
        }
    }

    public void loadForecastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        loadForecastWeatherFromAPI(city, startDate, endDate, null);
    }

    public void loadForecastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate, ProgressHandler progress) throws IOException {
        if(startDate.isBefore(LocalDate.now()) || endDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Cannot fetch forecast weather for past days");

        if(progress != null) progress.setMaxProgress((int)ChronoUnit.DAYS.between(startDate, endDate));

        MongoCollection<Document> collection = AppCollection.FORECAST_WEATHER.get(database);
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1)) {
            List<Document> mongoHourlyList = FetchAdapter.getInstance().fetchForecastData(city, d);
            addDataToCollection(collection, city, d, "weatherForecast", mongoHourlyList);
            if(progress != null) progress.increaseProgress();
        }
    }

    public void testMeasureImport(City city) throws IOException {
        for(int i=0; i<5; i++) {
            LocalDate d = LocalDate.now().minusDays(5 + i);
            List<Document> pollutionData = FetchAdapter.getInstance()
                    .fetchPollutionData(city, d);
            addDataToCollection(database.getCollection(AppCollection.POLLUTION.getName()), city, d, "pollutionMeasurements", pollutionData);
            List<Document> weatherCondition = FetchAdapter.getInstance()
                    .fetchHistoricalData(city, d);
            addDataToCollection(database.getCollection(AppCollection.PAST_WEATHER.getName()),city, d, "weatherCondition", weatherCondition);
        }

        List<Document> weatherForecast = FetchAdapter.getInstance().fetchForecastData(city, LocalDate.now());
        addDataToCollection(database.getCollection(AppCollection.FORECAST_WEATHER.getName()), city, LocalDate.now(), "weatherForecast", weatherForecast);
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


/*
    private void addDataToCollection (MongoCollection<Document> collection, City city, LocalDate day,
                                      String arrayname, List<Document> mongoHourlyList) {
        //start a client session
        ClientSession clientSession = mongoClient.startSession();

        // define options to use for the transaction
        TransactionOptions txnOptions = TransactionOptions.builder()
                .readPreference(ReadPreference.primary())
                .readConcern(ReadConcern.MAJORITY)      // study better
                .writeConcern(WriteConcern.MAJORITY)    // study better
                .build();

        //define the sequence of operations to perform inside the transaction
        TransactionBody txnBody = new TransactionBody<String>() {
            @Override
            public String execute() {
                LocalDateTime[] weekrange = FetchUtils.getWeekPeriod(day);
                LocalDateTime weekStart = weekrange[0];
                LocalDateTime weekEnd = weekrange[1];

                // Create query and update BSON Documents
                Document updatedoc = new Document()
                        .append("$setOnInsert", new Document()
                                .append("country",city.getCountry()).append("city", city.getCity())
                                .append("coordinates", new Document("type", "point").append("coordinates", city.getCoords().asList()))
                                .append("periodStart", weekStart)
                                .append("periodEnd", weekEnd)
                                .append("enabled", true)
                                .append(arrayname, mongoHourlyList));

                //filter document
                Document filterDoc = new Document("city", city.getCity())
                        .append("country", city.getCountry())
                        .append("periodStart", weekStart)
                        .append("periodEnd", weekEnd);

                // Update or insert (upsert) collection on MongoDB
                System.out.println(FetchUtils.toJson(filterDoc));
                System.out.println(FetchUtils.toJson(updatedoc));

                if (collection.updateOne(filterDoc, updatedoc, new UpdateOptions().upsert(true)).getMatchedCount() > 0) {
                    //array of operation to execute in bulk
                    List<UpdateOneModel<Document>> operations = new ArrayList<UpdateOneModel<Document>>();

                    for (Document measurement : mongoHourlyList) {
                        LocalDateTime datetime = (LocalDateTime) measurement.get("datetime");
                        List<Document> newMeasures = (List<Document>) measurement.get("measurements");

                        //the measurement must exist
                        Document findMeasurementDoc = new Document("city", city.getCity())
                                .append("country", city.getCountry())
                                .append("periodStart", weekStart)
                                .append("periodEnd", weekEnd);

                        String location = (String) measurement.get("location");
                        if (arrayname.equals("pollutionMeasurements"))
                            findMeasurementDoc.append(arrayname, new Document("$elemMatch", new Document("datetime", datetime).append("location", location)));
                        else
                            findMeasurementDoc.append(arrayname + ".datetime", datetime);

                        //if the measurement not exist add it
                        if (!collection.find(findMeasurementDoc).iterator().hasNext()) {
                            Document addMeasurementDoc = new Document("$push", new Document(arrayname, new Document("datetime", datetime).append("measurements", newMeasures)));
                            operations.add(new UpdateOneModel<>(filterDoc, addMeasurementDoc));
                        }
                        else {
                            List<Document> arrayFilters = new ArrayList<Document>();
                            switch (arrayname) {
                                case "pollutionMeasurements":
                                    arrayFilters.add(new Document("t.datetime", datetime).append("t.location", location));
                                    break;
                                default: arrayFilters.add(new Document("t.datetime", datetime));
                                    break;
                            }
                            UpdateOptions options = new UpdateOptions().arrayFilters(arrayFilters);


                            //pull existing measures
                        //    List<String> measurementNames = new ArrayList<>();
                        //   for (Document dd: newMeasures) {
                        //        measurementNames.add((String) dd.get("name"));
                            }
                        //    Document pullDoc = new Document("$pull", new Document(arrayname+".$[t].measurements", new Document("name", new Document("$in", measurementNames))));
                        //    operations.add(new UpdateOneModel<>(filterDoc, pullDoc, options));

                            //push new measures
                        //    Document pushDoc = new Document("$push", new Document(arrayname+".$[t].measurements", new Document("$each", newMeasures)));
                        //    operations.add(new UpdateOneModel<>(filterDoc, pushDoc, options));


                            //push new measures
                            Document pushDoc = new Document("$set", new Document(arrayname+".$[t].measurements", newMeasures));
                            operations.add(new UpdateOneModel<>(filterDoc, pushDoc, options));
                        }
                    }

                    if (operations.size() > 0)
                        collection.bulkWrite((List<? extends WriteModel<? extends Document>>) operations);
                }
                return "Inserted without duplicates";
            }
        };

        try {
            clientSession.withTransaction(txnBody, txnOptions);
            clientSession.commitTransaction();
        } catch (RuntimeException e) {
            e.printStackTrace();
            clientSession.abortTransaction();
        } finally {
            clientSession.close();
        }
    }
*/

    private void addDataToCollection (MongoCollection<Document> collection, City city, LocalDate day,
                                      String arrayname, List<Document> mongoHourlyList) {

        LocalDateTime[] weekrange = FetchUtils.getWeekPeriod(day);
        LocalDateTime weekStart = weekrange[0];
        LocalDateTime weekEnd = weekrange[1];

        // Create query and update BSON Documents
        Document updatedoc = new Document()
                .append("$setOnInsert", new Document()
                        .append("country",city.getCountry()).append("city", city.getCity())
                        .append("coordinates", new Document("type", "point").append("coordinates", city.getCoords().asList()))
                        .append("periodStart", weekStart)
                        .append("periodEnd", weekEnd)
                        .append("enabled", true)
                        .append(arrayname, mongoHourlyList));

        //filter document
        Document filterDoc = new Document("city", city.getCity())
                .append("country", city.getCountry())
                .append("periodStart", weekStart)
                .append("periodEnd", weekEnd);

        // Update or insert (upsert) collection on MongoDB
        System.out.println(FetchUtils.toJson(filterDoc));
        System.out.println(arrayname + " " + day);
        //System.out.println(FetchUtils.toJson(updatedoc));

        if (collection.updateOne(filterDoc, updatedoc, new UpdateOptions().upsert(true)).getMatchedCount() > 0) {
            //array of operation to execute in bulk
            List<UpdateOneModel<Document>> operations = new ArrayList<UpdateOneModel<Document>>();

            for (Document measurement : mongoHourlyList) {
                LocalDateTime datetime = (LocalDateTime) measurement.get("datetime");
                List<Document> newMeasures = (List<Document>) measurement.get("measurements");

                //pull measurement if exists
                Document pullDocument;
                if (arrayname.equals("pollutionMeasurements")) {
                    String location = (String) measurement.get("location");
                    pullDocument = new Document("$pull", new Document(arrayname, new Document("location", location).append("datetime", datetime)));
                } else
                    pullDocument = new Document("$pull", new Document(arrayname, new Document("datetime", datetime)));

                operations.add(new UpdateOneModel<>(filterDoc, pullDocument));
            }

            Document pushDocument = new Document("$push", new Document(arrayname, new Document("$each", mongoHourlyList)));
            operations.add(new UpdateOneModel<>(filterDoc, pushDocument));

            if (operations.size() > 0) {
                collection.bulkWrite((List<? extends WriteModel<? extends Document>>) operations);
                System.out.println("Bulk Update Done");
            }
        }
    }


    public HashSet<LocalDate> getPastWeatherAvailableDates(City city) {
        return getAvailableDates(city, AppCollection.PAST_WEATHER, "weatherCondition");
    }

    public HashSet<LocalDate> getForecastWeatherAvailableDates(City city) {
        return getAvailableDates(city, AppCollection.FORECAST_WEATHER, "weatherForecast");
    }

    public HashSet<LocalDate> getPollutionAvailableDates(City city) {
        return getAvailableDates(city, AppCollection.POLLUTION, "pollutionMeasurements");
    }

    public HashSet<LocalDate> getAvailableDates(City city, AppCollection appCollection, String arrayName) {
        List<Bson> pipeline = Arrays.asList(
                match(and(eq("city", city.getCity()), eq("country", city.getCountry())
                        )),
                unwind("$"+arrayName+""),
                project(fields(excludeId(), computed("year", eq("$year", "$"+arrayName+".datetime")),
                        computed("month", eq("$month", "$"+arrayName+".datetime")),
                        computed("day", eq("$dayOfMonth", "$"+arrayName+".datetime")))),
                group(and(eq("year", "$year"), eq("month", "$month"), eq("day", "$day")),
                        sum("count", 1L)),
                project(fields(excludeId(), computed("date", eq("$dateFromParts",
                        and(eq("year", "$_id.year"), eq("month", "$_id.month"),
                                eq("day", "$_id.day")))))));

        HashSet<LocalDate> resultSet = new HashSet<>();

        MongoCollection<Document> collection = appCollection.get(database);
        AggregateIterable<Document> aggregateIterable = collection.aggregate(pipeline);
        for(Document d : aggregateIterable)
            resultSet.add(d.get("date", LocalDateTime.class).toLocalDate());

        return resultSet;
    }

    // TODO: hours are not discretized
    public HashMap<City.CityName, ArrayList<MeasureValue>> getHourlyPollution(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        if(startDate.compareTo(endDate) > 0)
            return null;

        MongoCollection<Document> collection = AppCollection.POLLUTION.get(database);

        List<Bson> pipeline = Arrays.asList(
                    match(and(lte("periodStart", endDate), gte("periodEnd", startDate),
                            eq("city", selectedCity.getCity()),
                            eq("country", selectedCity.getCountry()))),
                    unwind("$pollutionMeasurements"),
                    match(and(lte("pollutionMeasurements.datetime", endDate),
                            gte("pollutionMeasurements.datetime", startDate))),
                    unwind("$pollutionMeasurements.measurements"),
                    project(fields(include("city", "country", "coordinfates"), excludeId(),
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


    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPollution(LocalDate startDate, LocalDate endDate, City selectedCity) {
        return getDailyPollution(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX), selectedCity);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPollution(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        if(startDate.compareTo(endDate) > 0)
            return null;

        MongoCollection<Document> collection = AppCollection.POLLUTION.get(database);

        List<Bson> pipeline = Arrays.asList(match(and(lt("periodStart",
                endDate), gte("periodEnd",
                startDate), eq("city", selectedCity.getCity()), eq("country", selectedCity.getCountry()))),
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

    public void updateUserStatus (User user, int status) {
        MongoCollection<Document> collection = AppCollection.USERS.get(database);

        collection.updateOne(
                eq("username", user.getUsername()),
                new Document("$set", new Document("status", status)));
    }

    public void voteLocation(User user, City.CityName cityName) {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        UpdateResult updateResult =
                collection.updateOne(and(eq("country", cityName.getCountry()), eq("city", cityName.getCity())),
                        new Document("$addToSet", new Document("votes", user.getUsername())));
        if(updateResult.getModifiedCount() < 1)
            throw new IllegalStateException("Already voted location");
    }

    public void unvoteLocation(User user, City.CityName cityName) {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        UpdateResult updateResult =
                collection.updateOne(and(eq("country", cityName.getCountry()), eq("city", cityName.getCity())),
                        new Document("$pull", new Document("votes", user.getUsername())));
        if(updateResult.getModifiedCount() < 1)
            throw new IllegalStateException("Not voted location");
    }


    /**
     * Update location status
     * @param city to be updated
     * @param enabled new status to apply
     * @return new location status (just for atomicity)
     */
    // TODO: we should update redundancy in all the measures collection
    public boolean updateCityStatus(City city, boolean enabled) {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);

        Document updatedLocationDocument = collection.findOneAndUpdate(
                and(eq("country", city.getCountry()), eq("city", city.getCity())),
                new Document("$set", new Document("enabled", enabled)),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));

        //update measurements documents
        String[] collectionNames = {AppCollection.POLLUTION.getName(), AppCollection.FORECAST_WEATHER.getName(), AppCollection.PAST_WEATHER.getName()};
        for (String collectionName : collectionNames) {
            collection = database.getCollection(collectionName);
            collection.updateMany(and(eq("country", city.getCountry()), eq("city", city.getCity())), new Document("$set", new Document("enabled", enabled)));
        }

        return updatedLocationDocument.getBoolean("enabled");
    }

    public ArrayList<City> getCitiesByStatus(boolean status) {
        MongoCollection<Document> collection = AppCollection.LOCATIONS.get(database);
        ArrayList<City> result = new ArrayList<>();

        MongoCursor<Document> cursor = collection.find(eq("enabled", status)).iterator();
        try {
            while (cursor.hasNext()) {
                Document d = cursor.next();
                Document d1 = (Document) d.get("coordinates");
                ArrayList<Double> coordinates = (ArrayList<Double>) d1.get("coordinates");
                City c = new City(d.getString("country"), d.getString("city"), d.getBoolean("enabled"),
                        new City.Coords(coordinates.get(0), coordinates.get(1)));
                result.add(c);
            }
        } finally {
            cursor.close();
            return result;
        }
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getHourlyForecastWeather(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        return getHourlyWeather(startDate, endDate, "weatherForecast", AppCollection.FORECAST_WEATHER, selectedCity);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getHourlyPastWeather(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        return getHourlyWeather(startDate, endDate, "weatherCondition", AppCollection.PAST_WEATHER, selectedCity);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getHourlyWeather(LocalDateTime startDate, LocalDateTime endDate, String arrayName, AppCollection collectionName, City selectedCity) {

        List<Bson> pipeline = Arrays.asList(match(and(lt("periodStart", endDate), gte("periodEnd", startDate)
                )), unwind("$"+arrayName),
                match(and(lt(arrayName+".datetime", endDate), gte(arrayName + ".datetime", startDate))),
                unwind("$"+arrayName+".measurements"),
                project(fields(include("city", "country", "coordinates"), excludeId(),
                        computed("weather.year", eq("$year", "$"+arrayName+".datetime")),
                        computed("weather.month", eq("$month", "$"+arrayName+".datetime")),
                        computed("weather.day", eq("$dayOfMonth", "$"+arrayName+".datetime")),
                        computed("weather.hour", eq("$hour", "$"+arrayName+".datetime")),
                        computed("measurement", "$"+arrayName+".measurements"))),
                group(and(eq("city", "$city"), eq("country", "$country"),
                        eq("year", "$weather.year"), eq("month", "$weather.month"),
                        eq("day", "$weather.day"), eq("hour", "$weather.hour"),
                        eq("condition", "$measurement.name"), eq("unit", "$measurement.unit")),
                        avg("avg", "$measurement.value"), push("list", and(eq("hour", "$weather.hour"),
                                eq("sky", "$measurement.value")))),
                new Document("$project", new Document("_id", "$_id").append("value", new Document("$cond", new Document("if",
                        new Document("$eq", Arrays.asList("$avg", new BsonNull()))).append("then", "$list").append("else", "$avg")))),
                group(and(eq("city", "$_id.city"), eq("country", "$_id.country")),
                        push("measurements", and(eq("datetime", eq("$dateFromParts",
                        and(eq("year", "$_id.year"), eq("month", "$_id.month"),
                        eq("day", "$_id.day"), eq("hour", "$_id.hour")))),
                        eq("condition", "$_id.condition"), eq("unit", "$_id.unit"),
                        eq("value", "$value")))),
                project(fields(excludeId(), computed("city", "$_id.city"),
                        computed("country", "$_id.country"), include("measurements"))));

        MongoCollection<Document> collection = collectionName.get(database);
        AggregateIterable<Document> aggregateList = collection.aggregate(pipeline);
        return parseWeatherList(aggregateList, "");
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPastWeather(LocalDate startDate, LocalDate endDate, City selectedCity) {
        return getDailyPastWeather(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX), selectedCity);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPastWeather(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        /*if(startDate.isAfter(LocalDateTime.now()) || endDate.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("cannot fetch future past weather data");*/
        return getDailyWeather(startDate, endDate, "weatherCondition", AppCollection.PAST_WEATHER, selectedCity);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyForecastWeather(LocalDate startDate, LocalDate endDate, City selectedCity) {
        return getDailyForecastWeather(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX), selectedCity);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyForecastWeather(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        return getDailyWeather(startDate, endDate, "weatherForecast", AppCollection.FORECAST_WEATHER, selectedCity);
    }

    private HashMap<City.CityName, ArrayList<MeasureValue>> getDailyWeather(LocalDateTime startDate, LocalDateTime endDate,
                                                                            String arrayName, AppCollection collectionName, City selectedCity) {
        List<Bson> pipeline = Arrays.asList(match(and(lte("periodStart", endDate), gte("periodEnd", startDate),
                eq("city", selectedCity.getCity()), eq("country", selectedCity.getCountry()))),
                unwind("$"+arrayName),
                match(and(lte(arrayName+".datetime", endDate), gte(arrayName+".datetime", startDate))),
                unwind("$"+arrayName+".measurements"),
                project(fields(include("city", "country", "coordinates"), excludeId(),
                        computed("weather.year", eq("$year", "$"+arrayName+".datetime")),
                        computed("weather.month", eq("$month", "$"+arrayName+".datetime")),
                        computed("weather.day", eq("$dayOfMonth", "$"+arrayName+".datetime")),
                        computed("weather.hour", eq("$hour", "$"+arrayName+".datetime")),
                        computed("measurement", "$"+arrayName+".measurements"))),
                group(and(eq("city", "$city"), eq("country", "$country"),
                        eq("year", "$weather.year"), eq("month", "$weather.month"),
                        eq("day", "$weather.day"), eq("condition", "$measurement.name"),
                        eq("unit", "$measurement.unit")), avg("avg", "$measurement.value"),
                        push("list", and(eq("hour", "$weather.hour"),
                                eq("sky", "$measurement.value")))),
                new Document("$project", new Document("_id", "$_id").append("value", new Document("$cond", new Document("if",
                        new Document("$eq", Arrays.asList("$avg", new BsonNull()))).append("then", "$list").append("else", "$avg")))),
                group(and(eq("city", "$_id.city"), eq("country", "$_id.country")),
                        push("measurements", and(eq("datetime", eq("$dateFromParts",
                                and(eq("year", "$_id.year"), eq("month", "$_id.month"),
                                        eq("day", "$_id.day")))), eq("condition", "$_id.condition"),
                                eq("unit", "$_id.unit"), eq("value", "$value")))),
                project(fields(excludeId(), computed("city", "$_id.city"),
                        computed("country", "$_id.country"), include("measurements"))));

        MongoCollection<Document> collection = collectionName.get(database);
        AggregateIterable<Document> aggregateList = collection.aggregate(pipeline);
        return parseWeatherList(aggregateList, "");
    }

    private HashMap<City.CityName, ArrayList<MeasureValue>> parseWeatherList(
            AggregateIterable<Document> aggregateList, String arrayName) {
        HashMap<City.CityName, ArrayList<MeasureValue>> cityMap = new HashMap<>();

        for(Document d : aggregateList) { // iterate by city first
            City.CityName city = new City.CityName(d.getString("country"), d.getString("city"));

            // iterate dates (day only)
            List<Document> daylist = d.getList("measurements", Document.class);
            for(Document dd : daylist) {
                LocalDateTime day = dd.get("datetime", LocalDateTime.class);
                // iterate weather measures
                MeasureValue m;
                if(dd.getString("condition").equals("sky")) {
                    HashMap<String, Integer> skyoccurences = new HashMap<>();
                    List<Document> skylist = dd.getList("value", Document.class);

                    // iterate sky statuses
                    for(Document ddd : skylist) {
                        String skystatus = ddd.get("sky", String.class);
                        if(!skyoccurences.containsKey(skystatus))
                            skyoccurences.put(skystatus, 0);
                        skyoccurences.put(skystatus, skyoccurences.get(skystatus) + 1);
                    }

                    // found max skystatus occurrence
                    int maxvalue = -1; String maxsky = "";
                    for(Map.Entry<String, Integer> entry : skyoccurences.entrySet())
                        if(entry.getValue() > maxvalue) {
                            maxvalue = entry.getValue();
                            maxsky = entry.getKey();
                        }

                    m = new MeasureValue(day, city, "sky", maxsky, "");
                }
                else
                    m = new MeasureValue(day, city,
                            dd.getString("condition"), dd.getDouble("value"), dd.getString("unit"));

                if(!cityMap.containsKey(city))
                    cityMap.put(city, new ArrayList<>());
                cityMap.get(city).add(m);
            }
        }

        return cityMap;
    }

    public void loadMeasure(LocalDateTime startDate, LocalDateTime endDate ) {

    }

    // TODO: we should add location range or location parameter to this function, and also to the others
    public ArrayList<MeasureValue> getWeatherForecastReliability(LocalDate startDate, LocalDate endDate, City selectedCity) {
        return getWeatherForecastReliability(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX), selectedCity);
    }

    public ArrayList<MeasureValue> getWeatherForecastReliability(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        ArrayList<MeasureValue> resultList = new ArrayList<>();

        HashMap<City.CityName, ArrayList<MeasureValue>> realWeather = getDailyPastWeather(startDate, endDate, selectedCity);
        HashMap<City.CityName, ArrayList<MeasureValue>> forecastWeather = getDailyForecastWeather(startDate, endDate, selectedCity);

        // oldForecast to HashMap<CityName, HashMap<MeasureTime, HashMap<MeasureType, MeasureValue>>
        /*HashMap<City.CityName, HashMap<LocalDateTime, HashMap<String, MeasureValue>>> oldForecastHash = new HashMap<>();
        for(Map.Entry<City.CityName, ArrayList<MeasureValue>> entry: oldForecast.entrySet())
            for(MeasureValue m : entry.getValue()) {
                if(!oldForecastHash.containsKey(entry.getKey()))
                    oldForecastHash.put(entry.getKey(), new HashMap<>());
                if(!oldForecastHash.get(entry.getKey()).containsKey(m.datetime))
                    oldForecastHash.get(entry.getKey()).put(m.datetime, new HashMap<>());
                oldForecastHash.get(entry.getKey()).get(m.datetime).put(m.name, m);
            }*/

        // oldForecast to HashMap<(CityName, datetime, measurename), MeasureValue>
        Function<MeasureValue, Integer> getHash = (m) -> Objects.hash(m.cityName, m.datetime, m.name);
        HashMap<Integer, MeasureValue> oldForecastHash = new HashMap<>();
        for(Map.Entry<City.CityName, ArrayList<MeasureValue>> entry: realWeather.entrySet())
            for(MeasureValue m : entry.getValue())
                oldForecastHash.put(getHash.apply(m), m);

        // iterate dailyForecast for computing measure errors
        for(Map.Entry<City.CityName, ArrayList<MeasureValue>> entry: forecastWeather.entrySet())
            for(MeasureValue mForecast : entry.getValue()) {
                MeasureValue mReal = oldForecastHash.get(getHash.apply(mForecast));
                if(mReal == null)
                    continue;

                // skip non numerical values
                if(!(mReal.getValue() instanceof Double))
                // mWeather should be also checked, however we are considering the same type of measure
                    continue;

                double relativeError = (mReal.<Double>getValue() - mForecast.<Double>getValue())/mReal.<Double>getValue();
                resultList.add(new MeasureValue(mReal.datetime, mReal.cityName, mReal.name, relativeError, "%"));
            }

        return resultList;
    }

    public ArrayList<MeasureValue> getPollutionForecast(LocalDate startDate, LocalDate endDate, City selectedCity) {
        return getPollutionForecast(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX), selectedCity);
    }

    public ArrayList<MeasureValue> getPollutionForecast(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        ArrayList<MeasureValue> resultList = new ArrayList<>();

        HashMap<City.CityName, ArrayList<MeasureValue>> currentPollution = getDailyPollution(LocalDateTime.now().minusDays(1), LocalDateTime.now(), selectedCity);
        HashMap<City.CityName, ArrayList<MeasureValue>> forecastWeather = getDailyForecastWeather(startDate, endDate, selectedCity);

        // oldForecast to HashMap<CityName, HashMap<MeasureTime, HashMap<MeasureType, MeasureValue>>
        /*HashMap<City.CityName, HashMap<LocalDateTime, HashMap<String, MeasureValue>>> oldForecastHash = new HashMap<>();
        for(Map.Entry<City.CityName, ArrayList<MeasureValue>> entry: oldForecast.entrySet())
            for(MeasureValue m : entry.getValue()) {
                if(!oldForecastHash.containsKey(entry.getKey()))
                    oldForecastHash.put(entry.getKey(), new HashMap<>());
                if(!oldForecastHash.get(entry.getKey()).containsKey(m.datetime))
                    oldForecastHash.get(entry.getKey()).put(m.datetime, new HashMap<>());
                oldForecastHash.get(entry.getKey()).get(m.datetime).put(m.name, m);
            }*/

        // currentPollution to HashMap<(CityName, datetime, measurename), MeasureValue>
        Function<MeasureValue, Integer> getHash = (m) -> Objects.hash(m.cityName, m.datetime, m.name);
        HashMap<Integer, MeasureValue> pollutionMeasurementHash = new HashMap<>();
        for(Map.Entry<City.CityName, ArrayList<MeasureValue>> entry: currentPollution.entrySet())
            for(MeasureValue m : entry.getValue())
                pollutionMeasurementHash.put(getHash.apply(m), m);

        // forecastWeather to HashMap<(CityName, datetime, measurename), MeasureValue>
        HashMap<Integer, MeasureValue> weatherForecastHash = new HashMap<>();
        for(Map.Entry<City.CityName, ArrayList<MeasureValue>> entry: forecastWeather.entrySet())
            for(MeasureValue m : entry.getValue())
                weatherForecastHash.put(getHash.apply(m), m);


        // compute pollutants values over the last 24h
        HashMap<Integer, MeasureValue> pollutionMeasurementAverageHash = new HashMap<>();
        for(Map.Entry<Integer, MeasureValue> entry : pollutionMeasurementHash.entrySet()) {
            MeasureValue m = entry.getValue();
            MeasureValue dualValue;
            if (m.datetime.getDayOfMonth() == LocalDateTime.now().getDayOfMonth())
                dualValue = pollutionMeasurementHash.get(getHash.apply(new MeasureValue(m.datetime.minusDays(1), m.cityName, m.name, m.value, m.unit)));
            else
                dualValue = pollutionMeasurementHash.get(getHash.apply(new MeasureValue(m.datetime.plusDays(1), m.cityName, m.name, m.value, m.unit)));
            MeasureValue avgValue = new MeasureValue(LocalDateTime.now(), m.cityName, m.name, ((Double) m.value + (dualValue == null ? (Double) m.value : (Double) dualValue.value)/2), m.unit);
            pollutionMeasurementAverageHash.put(getHash.apply(avgValue), avgValue);
        }


        for (Map.Entry<Integer, MeasureValue> pollutant : pollutionMeasurementAverageHash.entrySet()) {
            MeasureValue mPoll = pollutant.getValue();
            for (LocalDateTime i = startDate.toLocalDate().atStartOfDay(); i.getDayOfYear() <= endDate.getDayOfYear(); i = i.plusDays(1)){
                MeasureValue forecast = null;
                MeasureValue mWeather = null;
                switch (mPoll.name) {
                    case ("o3"): case ("no2"): case ("pm10"): case ("pm25"): case ("so2"): case ("co"): case ("bc"):
                        mWeather = weatherForecastHash.get(getHash.apply(
                                new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                }
                if (forecast != null)
                    resultList.add(forecast);
            }
        }

        return resultList;
    }

    private MeasureValue computePollutantForecast (MeasureValue p, MeasureValue w) {
        MeasureValue m = new MeasureValue(w.datetime, p.cityName, p.name, p.value, p.unit);
        if ((Double) w.value > 0.5)
            m.value = (Double) m.value * (Double) w.value;
        else
            m.value = (Double) m.value / (Double) w.value;
        return m;
    }


    public void dropAllCollections() {
        database.getCollection(AppCollection.USERS.getName()).drop();
        database.getCollection(AppCollection.LOCATIONS.getName()).drop();
        database.getCollection(AppCollection.POLLUTION.getName()).drop();
        database.getCollection(AppCollection.FORECAST_WEATHER.getName()).drop();
        database.getCollection(AppCollection.PAST_WEATHER.getName()).drop();
    }

    public static void main(String[] args) throws IOException {
        try {
            //MongoDBManager.getInstance().dropAllCollections();

            // Reload locations
            MongoDBManager.getInstance().resetLocationList();
            MongoDBManager.getInstance().createLocationIndex();
            MongoDBManager.getInstance().getLocationList(User.Status.ADMIN).forEach(c -> System.out.println(c.toString()));

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
            City cityRome = new City("IT", "Roma", true, new City.Coords(41.902782, 12.4963));
            MongoDBManager.getInstance().testMeasureImport(cityRome);

            // fetch pollution by hours and days
            System.out.println("Test getHourlyPollution:");
            HashMap<City.CityName, ArrayList<MeasureValue>> mres;
            mres = MongoDBManager.getInstance().getHourlyPollution(LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(5), cityRome);
            if (!mres.isEmpty())
                for(MeasureValue m : mres.get(cityRome.getCityName()))
                    System.out.println(m.toString());


            System.out.println("Test getDailyPollution:");
            mres = MongoDBManager.getInstance().getDailyPollution(LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(5), cityRome);
            if (!mres.isEmpty())
                for(MeasureValue m : mres.get(cityRome.getCityName()))
                    System.out.println(m.toString());

            System.out.println("Test getDailyWeather:");
            mres = MongoDBManager.getInstance().getDailyForecastWeather(LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(5), cityRome);
            if (!mres.isEmpty())
                for(MeasureValue m : mres.get(cityRome.getCityName()))
                    System.out.println(m.toString());

            ArrayList<MeasureValue> testGetPollutionForecast = MongoDBManager.getInstance().getPollutionForecast(LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7), cityRome);
            for (MeasureValue m : testGetPollutionForecast)
                System.out.println(m.toString());

            //test update user status
            System.out.println("Update Test");
            User user = new User("utente-s", "aldo", "aldo", User.Status.ENABLED);
            MongoDBManager.getInstance().updateUserStatus(user, User.Status.ADMIN.ordinal());

            //test update city status
            System.out.println("update city status");
            MongoDBManager.getInstance().updateCityStatus(cityRome, false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
