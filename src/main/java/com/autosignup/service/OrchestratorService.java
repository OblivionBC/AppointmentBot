package com.autosignup.service;

import com.autosignup.model.Appointment;
import com.autosignup.navigators.WebsiteNavigator;
import com.google.api.services.calendar.model.Event;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrchestratorService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final List<WebsiteNavigator> navigators;
    private final CalendarManager calendarManager;
    private final EmailService emailService;

    public void runAllNavigators() {
        logger.info("Starting execution of {} navigator(s)", navigators.size());
        for (WebsiteNavigator nav : navigators) {
            String navigatorName = nav.getClass().getSimpleName();
            logger.info("Running navigator: {}", navigatorName);
            nav.setEmailService(emailService);
            List<Appointment> appointments = nav.runAll();
            if (appointments.isEmpty()) {
                logger.info("Navigator {} returned no appointments (may be disabled or no matches found)", navigatorName);
            } else {
                logger.info("Navigator {} found {} appointment(s)", navigatorName, appointments.size());
                appointments.forEach(app -> {
                    Event e = calendarManager.createCalendarEvent(app);
                    emailService.sendEmailWithCalendarEvent(e);
                });
            }
        }
        logger.info("Completed execution of all navigators");
    }
}
