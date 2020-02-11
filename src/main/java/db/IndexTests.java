package main.java.db;

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import main.java.User;
import main.java.fetch.FetchUtils;
import org.bson.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

public class IndexTests {
    public static void main(String[] args) throws InterruptedException {
        testIndexUser();
        testIndexPollution();
        testIndexLocation();
    }

    public static int testWithRepetitions(Supplier<Document> testQuery) {
        int N = 30;

        int sum = 0;
        for(int i=0; i<N; i++) {
            Document explain = testQuery.get();
            sum += getExecutionTime(explain);
        }

        return sum/N;
    }

    private static void printExplain(Document explain) {
        Document executionStats = (Document)explain.get("executionStats");
        //int executionTimeMillis = executionStats.getInteger("executionTimeMillis");
        int totalKeysExamined = executionStats.getInteger("totalKeysExamined");
        int totalDocsExamined = executionStats.getInteger("totalDocsExamined");

        System.out.print("totalKeysExamined: " + totalKeysExamined + ", totalDocsExamined: " + totalDocsExamined);
    }

    private static int getExecutionTime(Document explain) {
        return ((Document)explain.get("executionStats")).getInteger("executionTimeMillis");
    }

    public static Document explainCommand(Document command) {
        return MongoDBManager.getInstance().database.runCommand(new Document("explain", command).append("verbosity", "executionStats"));
    }

    public static Document explainFind(MongoDBManager.AppCollection appCollection, Document filter) {
        return explainCommand(new Document("find", appCollection.getName())
                .append("filter", filter)
                .append("readConcern", appCollection.getReadConcern().asDocument()));
    }

    public static Document explainFind(MongoDBManager.AppCollection appCollection, Document filter, Collation collation) {
        return explainCommand(new Document("find", appCollection.getName())
                .append("filter", filter)
                .append("collation", collation.asDocument())
                .append("readConcern", appCollection.getReadConcern().asDocument()));
    }

    public static void testIndexUser() {
        Supplier<Document> testQuery =
                () -> explainFind(MongoDBManager.AppCollection.USERS, new Document("username", "admin"));
        Runnable prepareTest1 = () -> {try { MongoDBManager.getInstance().dropUserIndex(); } catch(MongoCommandException e) {}};
        Runnable prepareTest2 = () -> MongoDBManager.getInstance().createUserIndex();

        testIndex(testQuery, prepareTest1, prepareTest2, "USER index on (username)");
    }

    public static void testIndexPollution() {
        LocalDateTime[] weekPeriod = FetchUtils.getWeekPeriod(LocalDate.parse("2020-01-27"));
        Supplier<Document> testQuery = () -> explainFind(MongoDBManager.AppCollection.POLLUTION,
                new Document("country", "IT").append("city", "Roma").append("periodStart", weekPeriod[0])
                        .append("periodEnd", weekPeriod[1]));

        Runnable prepareTest1 = () -> { try {
            MongoDBManager.getInstance().dropMeasuresIndex(MongoDBManager.AppCollection.POLLUTION);
        } catch(MongoCommandException e) {}};

        Runnable prepareTest2 = () ->
                MongoDBManager.getInstance().createMeasuresIndex(MongoDBManager.AppCollection.POLLUTION);

        testIndex(testQuery, prepareTest1,prepareTest2, "POLLUTION index on (country,city,periodStart,periodEnd)");
    }

    public static void testIndexLocation() {
        Supplier<Document> testQuery =
                () -> explainFind(MongoDBManager.AppCollection.LOCATIONS,
                        new Document("country", "iT").append("city", "RoMa"),
                        Collation.builder().locale("en").collationStrength(CollationStrength.SECONDARY).build());
        Runnable prepareTest1 = () -> {try { MongoDBManager.getInstance().dropLocationIndex(); } catch(MongoCommandException e) {}};
        Runnable prepareTest2 = () -> MongoDBManager.getInstance().createLocationIndex();

        testIndex(testQuery, prepareTest1, prepareTest2, "LOCATION index on (country,city)");
    }

    public static void testIndex(Supplier<Document> testQuery, Runnable prepareTest1, Runnable prepareTest2, String headertext) {
        System.out.println("## " + headertext);

        prepareTest1.run();
        int test1 = testWithRepetitions(testQuery);
        System.out.print("[No INDEX] ");
        printExplain(testQuery.get());
        System.out.println(" execution Time: " + test1 + " ms");

        prepareTest2.run();
        int test2 = testWithRepetitions(testQuery);
        System.out.print("[With INDEX] ");
        printExplain(testQuery.get());
        System.out.println(" execution Time: " + test2 + " ms");

        System.out.println("## END\n");
    }

    public static void fillUsers(String[] fakeUsers) {
        for(int i=2; i<10; i++)
            for(String s : fakeUsers) {
                s = i + s;
                MongoDBManager.getInstance().registerUser(
                        new User(s, s + "Name", s + "Surname", User.Status.NOTENABLED), "password");
            }
    }
}