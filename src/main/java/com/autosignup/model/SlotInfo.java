package com.autosignup.model;

import com.microsoft.playwright.ElementHandle;

import java.time.LocalDate;

public record SlotInfo (ElementHandle element, String day, String time,
                        LocalDate appointmentDate, boolean available){

    @Override
    public String toString() {
        return String.format("SlotInfo{day='%s', time='%s', date=%s, available=%s}",
                day, time, appointmentDate, available);
    }
}
