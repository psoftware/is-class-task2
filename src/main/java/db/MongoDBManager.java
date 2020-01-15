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
import main.java.measures.MeasureValue;
import org.bson.BsonNull;
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
import java.util.function.Function;

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

    enum AppCollection {
        PAST_WEATHER("measureswpast"), FORECAST_WEATHER("measureswfor"),
        POLLUTION("measurespoll"), USERS("users"), LOCATIONS("locations");

        private final String name;
        AppCollection(String name) { this.name = name; }
        public String getName() { return name; }
    };

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
        MongoCollection<Document> collection = database.getCollection(AppCollection.LOCATIONS.getName());
        collection.deleteMany(new Document());
        collection.insertMany(FetchAdapter.getInstance().fetchAllCities());
    }

    public ArrayList<City> getLocationList() {
        ArrayList<City> resultList = new ArrayList<>();
        MongoCollection<Document> collection = database.getCollection(AppCollection.LOCATIONS.getName());
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
        MongoCollection<Document> collection = database.getCollection(AppCollection.USERS.getName());
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
        database.getCollection(AppCollection.USERS.getName()).createIndex(new Document("username", 1), new IndexOptions().unique(true));
    }

    public void loadPollutionFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        MongoCollection<Document> collection = database.getCollection(AppCollection.POLLUTION.getName());
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1))
            FetchAdapter.getInstance().fetchPollutionData(collection, city, d);
    }

    public void loadPastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        if(startDate.isAfter(LocalDate.now()) || endDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Cannot fetch past weather for future days");

        MongoCollection<Document> collection = database.getCollection(AppCollection.PAST_WEATHER.getName());
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1))
            FetchAdapter.getInstance().fetchHistoricalData(collection, city, d);
    }

    public void loadForecastWeatherFromAPI(City city, LocalDate startDate, LocalDate endDate) throws IOException {
        if(startDate.isBefore(LocalDate.now()) || endDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Cannot fetch forecast weather for past days");

        MongoCollection<Document> collection = database.getCollection(AppCollection.FORECAST_WEATHER.getName());
        for(LocalDate d = LocalDate.from(startDate); !d.equals(endDate.plusDays(1)); d = d.plusDays(1))
            FetchAdapter.getInstance().fetchForecastData(collection, city, d);
    }

    public void testMeasureImport(City city) throws IOException {
        for(int i=0; i<15; i++) {
            FetchAdapter.getInstance()
                    .fetchPollutionData(database.getCollection(AppCollection.POLLUTION.getName()), city, LocalDate.now().minusDays(5 + i));
            FetchAdapter.getInstance()
                    .fetchHistoricalData(database.getCollection(AppCollection.PAST_WEATHER.getName()), city, LocalDate.now().minusDays(5 + i));
        }

        FetchAdapter.getInstance().fetchForecastData(database.getCollection(AppCollection.FORECAST_WEATHER.getName()), city, LocalDate.now());
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
                match(and(eq("city", city.getCity()), eq("country", city.getCountry()),
                        eq("enabled", true))),
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

        MongoCollection<Document> collection = database.getCollection(appCollection.getName());
        AggregateIterable<Document> aggregateIterable = collection.aggregate(pipeline);
        for(Document d : aggregateIterable)
            resultSet.add(d.get("date", LocalDateTime.class).toLocalDate());

        return resultSet;
    }

    // TODO: hours are not discretized
    public HashMap<City.CityName, ArrayList<MeasureValue>> getHourlyPollution(LocalDateTime startDate, LocalDateTime endDate) {
        if(startDate.compareTo(endDate) > 0)
            return null;

        MongoCollection<Document> collection = database.getCollection(AppCollection.POLLUTION.getName());

        List<Bson> pipeline = Arrays.asList(
                    match(and(lte("periodStart", endDate), gte("periodEnd", startDate),
                        eq("enabled", true))),
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

        MongoCollection<Document> collection = database.getCollection(AppCollection.POLLUTION.getName());

        List<Bson> pipeline = Arrays.asList(match(and(lt("periodStart",
                endDate), gte("periodEnd",
                startDate), eq("enabled", true), eq("city", selectedCity.getCity()), eq("country", selectedCity.getCountry()))),
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
        MongoCollection<Document> collection = database.getCollection(AppCollection.USERS.getName());

        collection.updateOne(
                eq("username", user.getUsername()),
                new Document("$set", new Document("status", status)));
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPastWeather(LocalDate startDate, LocalDate endDate) {
        return getDailyPastWeather(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyPastWeather(LocalDateTime startDate, LocalDateTime endDate) {
        /*if(startDate.isAfter(LocalDateTime.now()) || endDate.isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("cannot fetch future past weather data");*/
        return getDailyWeather(startDate, endDate, "weatherCondition", AppCollection.PAST_WEATHER);
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyForecastWeather(LocalDate startDate, LocalDate endDate) {
        return getDailyForecastWeather(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));
    }

    public HashMap<City.CityName, ArrayList<MeasureValue>> getDailyForecastWeather(LocalDateTime startDate, LocalDateTime endDate) {
        return getDailyWeather(startDate, endDate, "weatherForecast", AppCollection.FORECAST_WEATHER);
    }

    private HashMap<City.CityName, ArrayList<MeasureValue>> getDailyWeather(LocalDateTime startDate, LocalDateTime endDate,
                                                                            String arrayName, AppCollection collectionName) {
        List<Bson> pipeline = Arrays.asList(match(and(lte("periodStart", endDate), gte("periodEnd", startDate), eq("enabled", true))),
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

        MongoCollection<Document> collection = database.getCollection(collectionName.getName());
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
    public ArrayList<MeasureValue> getWeatherForecastReliability(LocalDate startDate, LocalDate endDate) {
        return getWeatherForecastReliability(LocalDateTime.of(startDate, LocalTime.MIN), LocalDateTime.of(endDate, LocalTime.MAX));
    }

    public ArrayList<MeasureValue> getWeatherForecastReliability(LocalDateTime startDate, LocalDateTime endDate) {
        ArrayList<MeasureValue> resultList = new ArrayList<>();

        HashMap<City.CityName, ArrayList<MeasureValue>> realWeather = getDailyPastWeather(startDate, endDate);
        HashMap<City.CityName, ArrayList<MeasureValue>> forecastWeather = getDailyForecastWeather(startDate, endDate);

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


    public ArrayList<MeasureValue> getPollutionForecast(LocalDateTime startDate, LocalDateTime endDate, City selectedCity) {
        ArrayList<MeasureValue> resultList = new ArrayList<>();

        HashMap<City.CityName, ArrayList<MeasureValue>> currentPollution = getDailyPollution(LocalDateTime.now().minusDays(1), LocalDateTime.now(), selectedCity);
        HashMap<City.CityName, ArrayList<MeasureValue>> forecastWeather = getDailyForecastWeather(startDate, endDate);

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
                    case ("o3"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                    case ("no2"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                    case ("pm10"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                    case ("pm25"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                    case ("so2"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                    case ("co"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
                        if (mWeather != null)
                            forecast = computePollutantForecast(mPoll, mWeather);
                        break;
                    case ("bc"):
                        mWeather = weatherForecastHash.get(getHash.apply(new MeasureValue(i, mPoll.cityName, "humidity", mPoll.value, mPoll.unit)));
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
            mres = MongoDBManager.getInstance().getDailyPollution(LocalDateTime.now().minusDays(14), LocalDateTime.now().minusDays(5), cityRome);
            for(MeasureValue m : mres.get(cityRome.getCityName()))
                System.out.println(m.toString());

            ArrayList<MeasureValue> testGetPollutionForecast = MongoDBManager.getInstance().getPollutionForecast(LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(7), cityRome);
            for (MeasureValue m : testGetPollutionForecast)
                System.out.println(m.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
