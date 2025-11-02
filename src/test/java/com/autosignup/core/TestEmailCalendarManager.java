package com.autosignup.core;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.service.EmailCalendarManager;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class TestEmailCalendarManager {

    private EmailCalendarManager botEmailCalendarManager;
    @Before
    public void setUp() {
        botEmailCalendarManager = new EmailCalendarManager();
    }

    @Test
    public void testCreateEvent() {
        Appointment app = new Appointment(LocalDateTime.now(), LocalDateTime.now(), "Test Event",
                 "Test Event", "Test Description",
                "Test Location", AppointmentType.MASSAGE);
        String eventId = botEmailCalendarManager.createCalendarEvent(app);
        botEmailCalendarManager.removeCalendarEvent(eventId);
        assertNotNull(eventId);
    }
}