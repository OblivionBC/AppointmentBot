package com.autosignup.core;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.service.CalendarManager;
import com.autosignup.service.EmailService;
import com.google.api.services.calendar.model.Event;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TestEmailService {
    @Autowired
    private CalendarManager botCalendarManager;
    @Autowired
    private EmailService emailService;

    @Test
    public void testEventEmail() {
        Appointment app = new Appointment(LocalDateTime.now(), LocalDateTime.now(), "Test Event",
                "Test Event", "Test Description",
                "Test Location", AppointmentType.MASSAGE);
        Event event = botCalendarManager.createCalendarEvent(app);
        assertNotNull(event);
        emailService.sendEmailWithCalendarEvent(event);
        botCalendarManager.removeCalendarEvent(event.getId());
    }
}