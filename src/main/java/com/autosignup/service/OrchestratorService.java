package com.autosignup.service;

import com.autosignup.model.Appointment;
import com.autosignup.website.WebsiteNavigator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final List<WebsiteNavigator> navigators;
    private final EmailCalendarManager emailCalendarManager;

    //TODO: Test that this works
    public void runAllNavigators() {
        for (WebsiteNavigator nav : navigators) {
            List<Appointment> appointments = nav.runAll();
            appointments.forEach(app -> {
                emailCalendarManager.createCalendarEvent(app);
                emailCalendarManager.sendEmailNotification(app);
            });
        }
    }
}
