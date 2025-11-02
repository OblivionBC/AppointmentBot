package com.autosignup.model;

import java.time.LocalDateTime;

public record Appointment(LocalDateTime start,
                          LocalDateTime end, String eventName,
                          String summary,
                          String description, String location, AppointmentType appointmentType) {
}
