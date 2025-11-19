package com.autosignup.service;

import com.autosignup.model.Appointment;
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
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class CalendarManager {
    @Value("${credentials.path}")
    private static final Logger logger = LoggerFactory.getLogger(CalendarManager.class);
    private static final String CREDENTIALS_FILE_PATH = "src/main/resources/credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "AutoSignupBot";
    private static final String CALENDAR_ID = "primary";
    private final Calendar service;

    @Value("${calendar.attendee}")
    @Getter
    private String attendeeEmail;
    @Value("${calendar.timezone}")
    @Getter
    private String timezone;

    public CalendarManager() {
        service = getCalendarService();
    }

    public Event createCalendarEvent(Appointment appointment) {
        // Convert LocalDateTime to ZonedDateTime in the configured timezone
        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime startZoned = appointment.start().atZone(zoneId);
        ZonedDateTime endZoned = appointment.end().atZone(zoneId);
        
        // Format as RFC3339 for Google Calendar API
        DateTimeFormatter rfc3339Formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        String startStr = startZoned.format(rfc3339Formatter);
        String endStr = endZoned.format(rfc3339Formatter);
        
        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(startStr))
                .setTimeZone(timezone);

        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(endStr))
                .setTimeZone(timezone);

        Event event = new Event()
                .setSummary(appointment.summary())
                .setLocation(appointment.location())
                .setDescription(appointment.description());

        event.setStart(start);
        event.setEnd(end);

        event.setAttendees(List.of(new EventAttendee().setEmail(attendeeEmail)));

        EventReminder[] overrides = new EventReminder[]{
                new EventReminder().setMethod("popup").setMinutes(15),
                new EventReminder().setMethod("email").setMinutes(24 * 60)
        };
        event.setReminders(new Event.Reminders().setUseDefault(false).setOverrides(Arrays.asList(overrides)));

        try {
            Event created = service.events().insert(CALENDAR_ID, event)
                    .setSendUpdates("all")
                    .execute();
            return created;
        } catch (IOException e) {
            logger.error("Error when creating calendar event");
            throw new RuntimeException(e);
        }
    }

    public void removeCalendarEvent(String eventId) {
        try {
            service.events().delete(CALENDAR_ID, eventId).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Calendar getCalendarService() {
        try {
            var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = getCredentials();
            return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error getting calendar service");
            throw new RuntimeException(e);
        }
    }

    private static Credential getCredentials() throws IOException, GeneralSecurityException {
        var in = new java.io.File(CREDENTIALS_FILE_PATH);
        if (!in.exists()) {
            throw new IOException("Missing credentials.json file");
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
