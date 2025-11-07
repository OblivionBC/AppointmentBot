package com.autosignup.model.protocol;

import com.autosignup.model.SlotInfo;
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
    public boolean checkValidity(SlotInfo slot) {
        try{
            // Check 1: Verify no appointments exist in the week window
            LocalDate startOfWeek = slot.start().toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
            LocalDate endOfWeek = startOfWeek.plusDays(6L * windowWeeks);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String startStr = startOfWeek.atStartOfDay().format(fmt);
            String endStr = endOfWeek.atTime(23, 59, 59).format(fmt);

            String weekQuery = """
                SELECT COUNT(*) AS cnt
                FROM appointments
                WHERE appointment_type = ?
                  AND appointment_start_timestamp BETWEEN datetime(?) AND datetime(?)
            """;

            ResultSet rs = dbManager.runQuery(weekQuery, List.of(slot.appointmentType().toString(), startStr, endStr));
            if (rs.next() && rs.getInt("cnt") > 0) {
                System.out.println("Skipping slot due to existing appointment in window: " + slot.start());
                return false;
            }

            // Check 2: Verify no time overlaps with any existing appointments
            //TODO: Abstract this to the super class
            String slotStartStr = slot.start().format(fmt);
            String slotEndStr = slot.end().format(fmt);

            String overlapQuery = """
                SELECT COUNT(*) AS cnt
                FROM appointments
                WHERE appointment_type = ?
                  AND (
                    (datetime(appointment_start_timestamp) < datetime(?) AND datetime(appointment_end_timestamp) > datetime(?))
                    OR (datetime(appointment_start_timestamp) >= datetime(?) AND datetime(appointment_start_timestamp) < datetime(?))
                    OR (datetime(appointment_end_timestamp) > datetime(?) AND datetime(appointment_end_timestamp) <= datetime(?))
                  )
            """;

            ResultSet overlapRs = dbManager.runQuery(overlapQuery, 
                List.of(slot.appointmentType().toString(), 
                       slotEndStr, slotStartStr,
                       slotStartStr, slotEndStr,
                       slotStartStr, slotEndStr));

            if (overlapRs.next() && overlapRs.getInt("cnt") > 0) {
                System.out.println("Skipping slot due to time overlap with existing appointment: " + slot.start());
                return false;
            }

            return true;
        } catch (Exception e) {
            System.out.println("Error during validity check: " + e.getMessage());
            return false;
        }
    }
}
