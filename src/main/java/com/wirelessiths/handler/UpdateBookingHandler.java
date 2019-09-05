package com.wirelessiths.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wirelessiths.ApiGatewayResponse;
import com.wirelessiths.Response;
import com.wirelessiths.dal.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class UpdateBookingHandler implements RequestHandler<Map<String, Object>, ApiGatewayResponse> {

    private final Logger logger = LogManager.getLogger(this.getClass());


    @Override
    public ApiGatewayResponse handleRequest(Map<String, Object> input, Context context) {

        //TODO: Minska på tryen/ dela upp den
        try {

            ObjectMapper mapper = new ObjectMapper();
            // get the 'pathParameters' from input
            Map<String,String> pathParameters =  (Map<String,String>)input.get("pathParameters");
            String bookingId = pathParameters.get("id");

            // get the Product by id
            Booking booking = new Booking().get(bookingId);

            JsonNode body = new ObjectMapper().readTree((String) input.get("body"));

            UpdateBookingRequest updateBookingRequest = new UpdateBookingRequest();

            //booking = UpdateBookingHandler.setBookingProperties(updateBookingRequest, booking);


            boolean isNew = false;
            Booking newBooking = null;

            //TODO: Gör fler if (!null) koller på tex om json bodyn finns eller är tom

            // send the response back
            if (booking != null) {

                try {

                    try {
                        updateBookingRequest =  mapper.treeToValue(body, UpdateBookingRequest.class);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }


                    //TODO: Skriv tester
                    if ( body.has("endTime") || body.has("scooterId")) {

                        //TODO: Kolla om EndTime och ScooterId har rätt format (regex) Egen metod?
                        isNew = true;

                        newBooking = rewriteBooking(booking);
                    }

                    if(isNew) {

                        if(newBooking != null) {

                            newBooking = setBookingProperties(updateBookingRequest, newBooking);
                            newBooking.save(newBooking);
                        }
                    }
                    else {

                        booking = setBookingProperties(updateBookingRequest, booking);
                        booking.update(booking);
                    }


                    //TODO: Vad händer om skapande av ny bokning misslyckas?
                    //TODO: ...om inget i föregående if-sats händer (if(isNew))

                } catch (Exception e) {

                    logger.error("Error in retrieving product: " + e);

                    // send the error response back
                    Response responseBody = new Response( "Error in updating product: " + e.getMessage(), input);
                    return ApiGatewayResponse.builder()
                            .setStatusCode(500)
                            .setObjectBody(responseBody)
                            .setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
                            .build();
                }

                return ApiGatewayResponse.builder()
                        .setStatusCode(200)
                        .setObjectBody(booking)
                        .setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
                        .build();
            }
            else {

                return ApiGatewayResponse.builder()
                        .setStatusCode(404)
                        .setObjectBody("Product with id: '" + bookingId + "' not found.")
                        .setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
                        .build();
            }

        }catch (Exception ex) {

            logger.error("Error in retrieving product: " + ex);

            // send the error response back
            Response responseBody = new Response("Error in retrieving product: " + ex.getMessage(), input);
            return ApiGatewayResponse.builder()
                    .setStatusCode(500)
                    .setObjectBody(responseBody)
                    .setHeaders(Collections.singletonMap("X-Powered-By", "AWS Lambda & Serverless"))
                    .build();
        }
    }

    /**
     * Makes a booking object from updateBookingRequest. Should be used for updating object so that if no new info is inputted, the old information will persist.
     * @param updateBookingRequest - the booking request in where the new information is stored.
     * @param booking - the booking that you want to update.
     * @return updated booking
     */
    public static Booking setBookingProperties(UpdateBookingRequest updateBookingRequest, Booking booking) {

        //TODO: Vad göra om ändringen av något fält inte sker? p.g.a t.ex. regex, isPresent eller isEmpty
        //TODO: ...någon utskrift?
        //TODO: Hantera felaktiga datum?   t.ex. felaktiga datum (årtal)?
        //TODO: Ska fältet inte ändras om formatet är fel? hur ska detta meddelas så man kan lösa de?


        //TODO: Blir det rätt format i våran kod?
        //TODO: Vad göra om det inte stämmer?   felhantering
        //TODO: Constraints på tidigast och senast årtal?
        //TODO: Använda i createBookingHandler?
        Pattern DATE_PATTERN = Pattern.compile(
                "^((2000|2400|2800|(19|2[0-9](0[48]|[2468][048]|[13579][26])))-02-29)$"
                        + "|^(((19|2[0-9])[0-9]{2})-02-(0[1-9]|1[0-9]|2[0-8]))$"
                        + "|^(((19|2[0-9])[0-9]{2})-(0[13578]|10|12)-(0[1-9]|[12][0-9]|3[01]))$"
                        + "|^(((19|2[0-9])[0-9]{2})-(0[469]|11)-(0[1-9]|[12][0-9]|30))$");

        Pattern TIME_PATTERN = Pattern.compile("([01]?[0-9]|2[0-3]):[0-5][0-9]");

        //TODO: Funkar inte att slå ihop patterns såhär
        Pattern ISO_INSTANT = Pattern.compile(DATE_PATTERN + "T" + TIME_PATTERN + ".\\d?\\d?\\d?\\d?\\d?\\d?\\d?\\d?\\d?Z");



        //TODO: Skriva om optional.if present funktionerna så man kan lägga in .isEmpty efter ifPresent checken
        //TODO: Lägg varje ifPresent i if satsar istället för som de är nu?
        //TODO: Separata Optional.ofNullable för varje fält som ska kollas?
        Optional.ofNullable(updateBookingRequest).ifPresent(optUpdateRequest -> {
                optUpdateRequest.getUserId().filter(s -> !s.isEmpty()).ifPresent(booking::setUserId);
                optUpdateRequest.getScooterId().filter(s -> !s.isEmpty()).ifPresent(booking::setScooterId);
                optUpdateRequest.getBookingId().filter(s -> !s.isEmpty()).ifPresent(booking::setBookingId);

                optUpdateRequest.getDate().ifPresent(n -> {
                    if (n.matches(String.valueOf(DATE_PATTERN))) {
                        try {
                            LocalDateConverter converter = new LocalDateConverter();
                            booking.setDate(converter.unconvert(n));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                //TODO: Instant.parse i try & catch?  catch: DateTimeParseException?? som enda format check på startTime och endTime
                //TODO: Constraints på tidigast och senast årtal?  Instant.isBefore() Instant.isAfter()
                //TODO: Andra checkar som att startTime är mindre/tidigare än endTime eller om nån av tiderna redan har vart
                //TODO: ska dom checkarna göras här?
                // "2019-08-30T16:00:36.739Z"
                optUpdateRequest.getStartTime().ifPresent(n -> {
                    if (!n.matches("")) {
                        try {
                            InstantConverter converter = new InstantConverter();
                            booking.setStartTime(converter.unconvert(n));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            //TODO: Constraints på tidigast och senast årtal?
                optUpdateRequest.getEndTime().ifPresent(n -> {
                    if(!n.matches("")) {
                        try {
                            InstantConverter converter = new InstantConverter();
                            booking.setEndTime(converter.unconvert(n));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                optUpdateRequest.getTripStatus().ifPresent(n -> {
                    if (n.equals("WAITING_TO_START") || n.equals("IN_PROGRESS") || n.equals("COMPLETED") || n.equals("SCOOTER_NOT_RETURNED")) {
                        TripStatus tripStatus = TripStatus.valueOf(n);
                        booking.setTripStatus(tripStatus);
                    }

                });


            } );





        return booking;
    }


    public static Booking rewriteBooking(Booking booking) {

        boolean isDeleted = false;

        try {
            Booking newBooking = new Booking().get(booking.getBookingId());
            isDeleted = booking.delete(booking.getBookingId());

            if(isDeleted) {   //TODO: Nödvändig koll?    Vad händer om deleten misslyckas men ändå returner true? eller Tvärtom?
                return newBooking;
            }

        }catch (Exception e) {     //TODO: Utförligare try&catch
            e.printStackTrace();
        }

        return null;
    }

}
