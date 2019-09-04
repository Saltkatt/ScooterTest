package com.wirelessiths.dal;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import com.wirelessiths.exception.CouldNotCreateBookingException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Objects;


/**
 *This class contains the variables required in a booking.
 *It also creates a database client in AWSDynamoDb and takes advantage of the AutoGeneratedKey function.
 */

@DynamoDBTable(tableName = "PLACEHOLDER_BOOKINGS_TABLE_NAME")
public class Booking {

    // get the table name from env. var. set in serverless.yml
    private static final String BOOKINGS_TABLE_NAME = System.getenv("BOOKINGS_TABLE_NAME");

    private String scooterId;
    private String bookingId;
    private String userId;

    private Instant startTime;
    private Instant endTime;
    private LocalDate date;

    private TripStatus tripStatus;


    private static DynamoDBAdapter db_adapter;
    private final AmazonDynamoDB client;
    private final DynamoDBMapper mapper;
    private final DynamoDB dynamoDB;

    //private final Logger logger = LogManager.getLogger(this.getClass());
    private final LoggerAdapter logger;
    private final StringBuilder sb = new StringBuilder();

   /**
     *This method connects to DynamoDB, creates a table with a mapperConfig.
     */
    public Booking() {
        // build the mapper config
        DynamoDBMapperConfig mapperConfig = DynamoDBMapperConfig.builder()
                .withTableNameOverride(new DynamoDBMapperConfig.TableNameOverride(BOOKINGS_TABLE_NAME))
                .build();
        // get the db adapter
        this.db_adapter = DynamoDBAdapter.getInstance();
        this.client = this.db_adapter.getDbClient();
        this.dynamoDB = this.db_adapter.getDynamoDB();
        // create the mapper with config
        this.mapper = this.db_adapter.createDbMapper(mapperConfig);

        this.logger = new LoggerAdapter(LogManager.getLogger(this.getClass()));
    }

    public Booking(AmazonDynamoDB client, DynamoDBMapperConfig config){
        this.client = client;
        this.dynamoDB = new DynamoDB(client);
        this.mapper = new DynamoDBMapper(client, config);
        this.logger = new LoggerAdapter();
        //this.logger = LogManager.getLogger(this.getClass());
    }


    @DynamoDBHashKey(attributeName = "scooterId")
    public String getScooterId() {
        return this.scooterId;
    }
    public void setScooterId(String scooterId) {
        this.scooterId = scooterId;
    }


    @DynamoDBRangeKey(attributeName = "endTime")
    @DynamoDBAttribute(attributeName = "endTime")
    @DynamoDBTypeConverted( converter = InstantConverter.class )
    public Instant getEndTime() {
        return endTime;
    }
    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }


    @DynamoDBIndexRangeKey(attributeName = "startTime", globalSecondaryIndexName = "dateIndex")
    @DynamoDBTypeConverted( converter = InstantConverter.class )
    public Instant getStartTime() {
        return startTime;
    }
    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }


    @DynamoDBIndexHashKey(attributeName = "date", globalSecondaryIndexName = "dateIndex")
    @DynamoDBTypeConverted( converter = LocalDateConverter.class )
    public LocalDate getDate() {
        return date;
    }
    public void setDate(LocalDate date) {
        this.date = date;
    }

    @DynamoDBIndexHashKey(attributeName = "bookingId", globalSecondaryIndexName = "bookingIndex")
    @DynamoDBAutoGeneratedKey
    public String getBookingId() {
        return bookingId;
    }
    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    @DynamoDBIndexHashKey(attributeName = "userId", globalSecondaryIndexName = "userIndex")
    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName="tripStatus")
    public TripStatus getTripStatus() {
        return tripStatus;
    }

    public void setTripStatus(TripStatus tripStatus) {
        this.tripStatus = tripStatus;
    }

    @Override
    public String toString() {
        return "Booking{" +
                "scooterId='" + scooterId + '\'' +
                ", bookingId='" + bookingId + '\'' +
                ", userId='" + userId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", date=" + date +
                ", tripStatus=" + tripStatus +
                '}';
    }


    public List<Booking> validateBooking(Booking booking) throws IOException{

        int maxDurationSeconds = 60 * 60 * 7;//temporary hardcoding of 7 hour max booking length

        String start = booking.getStartTime().toString();
        String end = booking.getEndTime().toString();
        String endPlusMaxDur = booking.getEndTime().plusSeconds(maxDurationSeconds).toString();

        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":id", new AttributeValue().withS(booking.getScooterId()));
        values.put(":start", new AttributeValue().withS(start));
        values.put(":endPlusMaxDur", new AttributeValue().withS(endPlusMaxDur));
        values.put(":end", new AttributeValue().withS(end));

        DynamoDBQueryExpression<Booking> queryExp = new DynamoDBQueryExpression<>();
        queryExp.withKeyConditionExpression("scooterId = :id and endTime between :start and :endPlusMaxDur")
                .withExpressionAttributeValues(values)
                .withConsistentRead(true)
                .withFilterExpression("startTime < :end");

        return mapper.query(Booking.class, queryExp);
    }

        // methods
    public Boolean ifTableExists() {
        return this.client.describeTable(BOOKINGS_TABLE_NAME).getTable().getTableStatus().equals("ACTIVE");
    }

    public List<Booking> list() throws IOException {
        DynamoDBScanExpression scanExp = new DynamoDBScanExpression();
        List<Booking> results = this.mapper.scan(Booking.class, scanExp);
        for (Booking p : results) {
            logger.info("Booking - list(): " + p.toString());
        }
        return results;
    }

    public Booking get(String id) throws IOException {
        Booking booking = null;

        HashMap<String, AttributeValue> av = new HashMap<String, AttributeValue>();
        av.put(":v1", new AttributeValue().withS(id));

        DynamoDBQueryExpression<Booking> queryExp = new DynamoDBQueryExpression<Booking>()
                .withKeyConditionExpression("bookingId = :v1")
                .withExpressionAttributeValues(av)
                .withConsistentRead(false);
        queryExp.setIndexName("bookingIndex");

        PaginatedQueryList<Booking> result = this.mapper.query(Booking.class, queryExp);
        if (result.size() > 0) {
            booking = result.get(0);
            logger.info("Booking - get(): booking - " + booking.toString());
        } else {
            logger.info("Booking - get(): booking - Not Found.");
        }
        return booking;
    }

    public List<Booking> getByUserId(String userId) throws IOException {

        // Query with mapper
        // Create Booking object with user id
        Booking booking = new Booking();
        booking.setUserId(userId);

        //Input this and the gsi index name in query expression
        DynamoDBQueryExpression<Booking> queryExpression =
                new DynamoDBQueryExpression<>();
        queryExpression.setHashKeyValues(booking);
        queryExpression.setIndexName("userIndex");
        queryExpression.setConsistentRead(false);
        final List<Booking> results =
                mapper.query(Booking.class, queryExpression);

        return results;
    }

    public Booking save(Booking booking) throws IOException {

            logger.info("Booking - save(): " + booking.toString());
            this.mapper.save(booking);
            return booking;
    }

    public void update(Booking booking) throws  IOException {   //TODO:  throw IOException/try&catch?

        logger.info("User - update(): " + booking.toString());
        //TODO: Optimistic Locking och Condition Expressions???

        DynamoDBMapperConfig dynamoDBMapperConfig = new DynamoDBMapperConfig.Builder()
                .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.UPDATE_SKIP_NULL_ATTRIBUTES)
                .build();
        this.mapper.save(booking, dynamoDBMapperConfig);

    }

    public Boolean delete(String id) throws IOException {
        Booking booking = null;
        // get product if exists
        booking = get(id);
        if (booking != null) {
            logger.info("Booking - delete(): " + booking.toString());
            this.mapper.delete(booking);
        } else {
            logger.info("Booking - delete(): booking - does not exist.");
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Booking booking = (Booking) o;
        return scooterId.equals(booking.scooterId) &&
                bookingId.equals(booking.bookingId) &&
                userId.equals(booking.userId) &&
                startTime.equals(booking.startTime) &&
                endTime.equals(booking.endTime) &&
                date.equals(booking.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scooterId, bookingId, userId, startTime, endTime, date);
    }
}
