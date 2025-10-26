package com.autosignup;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

class GoogleCalendarApiTest {

    private static final String CREDENTIALS_FILE_PATH = "src/main/resources/credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "AutoSignupBot";
    private static final String CALENDAR_ID = "primary";
    private Calendar service;
    private String attendeeEmail;

    @BeforeEach
    public void setup() throws GeneralSecurityException, IOException {
        service = getCalendarService();
        attendeeEmail = System.getenv("SMTP_TO");

        if (attendeeEmail == null || attendeeEmail.isEmpty()) {
            attendeeEmail = "hengstler2005@gmail.com";
        }
    }

    @Test
    void testEmailWithCalendar() {
        try {
            LocalDate eventDate = LocalDate.now().plusDays(1);
            while (!eventDate.getDayOfWeek().toString().equals("TUESDAY")) {
                eventDate = eventDate.plusDays(1);
            }
            LocalTime startTime = LocalTime.of(17, 45);
            LocalTime endTime = LocalTime.of(18, 35);

            String startStr = String.format("%sT%s:00-07:00", eventDate, startTime);
            String endStr = String.format("%sT%s:00-07:00", eventDate, endTime);

            EventDateTime start = new EventDateTime()
                    .setDateTime(new com.google.api.client.util.DateTime(startStr))
                    .setTimeZone("America/Los_Angeles");

            EventDateTime end = new EventDateTime()
                    .setDateTime(new com.google.api.client.util.DateTime(endStr))
                    .setTimeZone("America/Los_Angeles");

            Event event = new Event()
                    .setSummary("TEST APPOINTMENT - Google Calendar API ")
                    .setLocation("Fake Location")
                    .setDescription("Hi this is my location!");

            event.setStart(start);
            event.setEnd(end);

            event.setAttendees(List.of(new EventAttendee().setEmail(attendeeEmail)));

            EventReminder[] overrides = new EventReminder[]{
                    new EventReminder().setMethod("popup").setMinutes(15),
                    new EventReminder().setMethod("email").setMinutes(24 * 60)
            };
            event.setReminders(new Event.Reminders().setUseDefault(false).setOverrides(Arrays.asList(overrides)));

            Event created = service.events().insert(CALENDAR_ID, event)
                    .setSendUpdates("all")
                    .execute();

        } catch (Exception e) {
            System.err.println("\nERROR: " + e.getMessage());
            Assertions.fail("System Errored");
        }
    }

    private static Calendar getCalendarService() throws IOException, GeneralSecurityException {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials();
        return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private static Credential getCredentials() throws IOException, GeneralSecurityException {
        var in = new java.io.File(CREDENTIALS_FILE_PATH);
        if (!in.exists()) {
            throw new IOException("Missing credentials.json file at " + CREDENTIALS_FILE_PATH);
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(in));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                clientSecrets,
                Collections.singleton(CalendarScopes.CALENDAR))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(8890).build())
                .authorize("user");
    }
}
