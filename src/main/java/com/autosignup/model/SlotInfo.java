package com.autosignup.model;

import com.microsoft.playwright.ElementHandle;

import java.time.LocalDateTime;

public record SlotInfo (ElementHandle element, String day, String time,
                        LocalDateTime start, LocalDateTime end, 
                        boolean available, AppointmentType appointmentType) {

    @Override
    public String toString() {
        return String.format("SlotInfo{day='%s', time='%s', start=%s, end=%s, available=%s, type=%s}",
                day, time, start, end, available, appointmentType);
    }
}
