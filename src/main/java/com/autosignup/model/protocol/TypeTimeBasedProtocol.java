package com.autosignup.model.protocol;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.service.BotDBManager;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;

public class TypeTimeBasedProtocol extends SignupProtocol {

    private final int windowHours;

    public TypeTimeBasedProtocol(BotDBManager dbManager, int windowHours) {
        super(dbManager);
        this.windowHours = windowHours;
    }

    @Override
    public boolean checkValidity(Appointment appointment) {
        try {
            AppointmentType type = appointment.appointmentType();
            LocalDateTime start = appointment.start();
            LocalDateTime windowStart = start.minusHours(windowHours);
            LocalDateTime windowEnd = start.plusHours(windowHours);

            String query = """
                    SELECT COUNT(*) AS cnt FROM appointments
                    WHERE type = ?
                      AND start BETWEEN datetime(?) AND datetime(?)
                    """;

            ResultSet rs = dbManager.runQuery(query, List.of(type.name(), windowStart, windowEnd));
            if (rs.next() && rs.getInt("cnt") > 0) {
                System.out.println("Skipping appointment {} due to existing in window." + appointment.start());
                return false;
            }
            return true;
        } catch (Exception e) {
            System.out.println("Error during validity check " + e.getMessage());
            return false;
        }
    }
}
