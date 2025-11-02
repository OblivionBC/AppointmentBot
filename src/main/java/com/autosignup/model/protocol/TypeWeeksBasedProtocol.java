package com.autosignup.model.protocol;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.service.BotDBManager;

import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


public class TypeWeeksBasedProtocol extends SignupProtocol {
    private final int windowWeeks;

    public TypeWeeksBasedProtocol(BotDBManager dbManager, int windowWeeks) {
        super(dbManager);
        this.windowWeeks = windowWeeks;
    }

    @Override
    public boolean checkValidity(Appointment appointment) {
        try{
            LocalDate startOfWeek = appointment.start().toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            LocalDate endOfWeek = startOfWeek.plusDays(6L * windowWeeks);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startStr = startOfWeek.atStartOfDay().format(fmt);
            String endStr = endOfWeek.atTime(23, 59, 59).format(fmt);

            String query = """
                SELECT COUNT(*) AS cnt
                FROM appointments
                WHERE strftime('%s', appointment_timestamp)
                      BETWEEN strftime('%s', ?) AND strftime('%s', ?)
            """;

            ResultSet rs = dbManager.runQuery(query, List.of(startStr, endStr));
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
