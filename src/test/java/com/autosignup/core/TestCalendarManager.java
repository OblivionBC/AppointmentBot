package com.autosignup.core;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.service.CalendarManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestCalendarManager {
    @Autowired
    private CalendarManager botCalendarManager;
    @Before
    public void setUp() {
        assertNotNull(botCalendarManager.getAttendeeEmail());
        assertNotNull(botCalendarManager.getTimezone());
    }

    @Test
    public void testCreateEvent() {
        Appointment app = new Appointment(LocalDateTime.now(), LocalDateTime.now(), "Test Event",
                 "Test Event", "Test Description",
                "Test Location", AppointmentType.MASSAGE);
        String eventId = botCalendarManager.createCalendarEvent(app).getId();
        botCalendarManager.removeCalendarEvent(eventId);
        assertNotNull(eventId);
    }
}