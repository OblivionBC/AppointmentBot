package com.autosignup.core;

import com.autosignup.model.AppointmentType;
import com.autosignup.model.SlotInfo;
import com.autosignup.model.Signup;
import com.autosignup.model.protocol.TypeWeeksBasedProtocol;
import com.autosignup.service.BotDBManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class TestTypeWeeksProtocol {
    private BotDBManager botDBManager;
    private TypeWeeksBasedProtocol protocol1Week;
    private TypeWeeksBasedProtocol protocol2Weeks;
    private static final String TEST_DB_PATH = "test-protocol.db";

    @Before
    public void setup() {
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();

        botDBManager = new BotDBManager("jdbc:sqlite:" + TEST_DB_PATH);
        
        protocol1Week = new TypeWeeksBasedProtocol(botDBManager, 1);
        protocol2Weeks = new TypeWeeksBasedProtocol(botDBManager, 2);
    }

    @After
    public void teardown() {
        botDBManager.close();
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
    }

    private SlotInfo createTestSlot(LocalDateTime start, LocalDateTime end, AppointmentType type, boolean available) {
        return new SlotInfo(
            null, // ElementHandle not needed for protocol tests
            start.getDayOfWeek().toString(),
            start.toLocalTime().toString(),
            start,
            end,
            available,
            type
        );
    }

    @Test
    public void testValidAppointment() {
        LocalDateTime start = LocalDateTime.of(2025, 11, 10, 14, 0);
        LocalDateTime end = start.plusHours(1);
        SlotInfo slot = createTestSlot(start, end, AppointmentType.MASSAGE, true);

        boolean isValid = protocol1Week.checkValidity(slot);
        assertTrue("New appointment with no conflicts should be valid", isValid);
    }

    @Test
    public void testWeekWindowConflict() {
        LocalDateTime existingStart = LocalDateTime.of(2025, 11, 10, 14, 0); // Monday
        LocalDateTime existingEnd = existingStart.plusHours(1);
        
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                existingStart, existingEnd, "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime newStart = LocalDateTime.of(2025, 11, 13, 16, 0); // Thursday same week
        LocalDateTime newEnd = newStart.plusHours(1);
        SlotInfo newSlot = createTestSlot(newStart, newEnd, AppointmentType.MASSAGE, true);

        boolean isValid = protocol1Week.checkValidity(newSlot);
        assertFalse("Appointment in same week window should be rejected", isValid);
    }

    @Test
    public void testTimeOverlap() {
        LocalDateTime existingStart = LocalDateTime.of(2025, 11, 10, 14, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2025, 11, 10, 15, 0);
        
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                existingStart, existingEnd, "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime newStart = LocalDateTime.of(2025, 11, 24, 14, 30); // Two weeks later
        LocalDateTime newEnd = LocalDateTime.of(2025, 11, 24, 15, 30);
        SlotInfo newSlot = createTestSlot(newStart, newEnd, AppointmentType.MASSAGE, true);

        newStart = LocalDateTime.of(2025, 11, 10, 14, 30); // Same day, overlapping time
        newEnd = LocalDateTime.of(2025, 11, 10, 15, 30);
        newSlot = createTestSlot(newStart, newEnd, AppointmentType.MASSAGE, true);

        boolean isValid = protocol1Week.checkValidity(newSlot);
        assertFalse("Overlapping appointment should be rejected", isValid);
    }

    @Test
    public void testMultipleWeekWindow() {
        LocalDateTime existingStart = LocalDateTime.of(2025, 11, 10, 14, 0);
        LocalDateTime existingEnd = existingStart.plusHours(1);
        
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                existingStart, existingEnd, "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime sameWeekStart = LocalDateTime.of(2025, 11, 15, 14, 0);
        LocalDateTime sameWeekEnd = sameWeekStart.plusHours(1);
        SlotInfo sameWeekSlot = createTestSlot(sameWeekStart, sameWeekEnd, AppointmentType.MASSAGE, true);

        boolean isSameWeekValid = protocol1Week.checkValidity(sameWeekSlot);
        assertFalse("Appointment in same week should be rejected by 1-week protocol", isSameWeekValid);
        
        LocalDateTime nextWeekStart = LocalDateTime.of(2025, 11, 20, 14, 0);
        LocalDateTime nextWeekEnd = nextWeekStart.plusHours(1);
        SlotInfo nextWeekSlot = createTestSlot(nextWeekStart, nextWeekEnd, AppointmentType.MASSAGE, true);

        boolean isNextWeekValid1Week = protocol1Week.checkValidity(nextWeekSlot);
        assertTrue("Appointment in next week should be valid with 1-week protocol", isNextWeekValid1Week);

        boolean isNextWeekValid2Weeks = protocol2Weeks.checkValidity(nextWeekSlot);
        assertTrue("Appointment in next week should be valid with 2-week protocol", isNextWeekValid2Weeks);
        
        LocalDateTime secondStart = LocalDateTime.of(2025, 11, 17, 14, 0);
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                secondStart, secondStart.plusHours(1), "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));
        
        boolean isNowValid = protocol1Week.checkValidity(nextWeekSlot);
        assertFalse("Appointment in same week as existing appointment should be rejected", isNowValid);
    }

    @Test
    public void testEdgeCasesSameDayDifferentTime() {
        LocalDateTime existingStart = LocalDateTime.of(2025, 11, 10, 10, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2025, 11, 10, 11, 0);
        
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                existingStart, existingEnd, "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime newStart = LocalDateTime.of(2025, 11, 10, 14, 0);
        LocalDateTime newEnd = LocalDateTime.of(2025, 11, 10, 15, 0);
        SlotInfo newSlot = createTestSlot(newStart, newEnd, AppointmentType.MASSAGE, true);

        boolean isValid = protocol1Week.checkValidity(newSlot);
        assertFalse("Appointment on same day within week window should be rejected (even with different time)", isValid);
    }

    @Test
    public void testEdgeCasesBoundaryTimes() {
        LocalDateTime existingStart = LocalDateTime.of(2025, 11, 10, 14, 0);
        LocalDateTime existingEnd = LocalDateTime.of(2025, 11, 10, 15, 0);
        
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                existingStart, existingEnd, "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime newStart = LocalDateTime.of(2025, 11, 24, 15, 0); // Two weeks later, starts at end time
        LocalDateTime newEnd = LocalDateTime.of(2025, 11, 24, 16, 0);
        SlotInfo newSlot = createTestSlot(newStart, newEnd, AppointmentType.MASSAGE, true);

        boolean isValid = protocol1Week.checkValidity(newSlot);
        assertTrue("Appointment starting at previous end time in different week should be valid", isValid);
        
        newStart = LocalDateTime.of(2025, 11, 10, 13, 0);
        newEnd = LocalDateTime.of(2025, 11, 10, 14, 0);
        newSlot = createTestSlot(newStart, newEnd, AppointmentType.MASSAGE, true);
        
        isValid = protocol1Week.checkValidity(newSlot);
        assertFalse("Appointment on same day should be rejected due to week window", isValid);
    }

    @Test
    public void testEdgeCasesDifferentAppointmentTypes() {
        LocalDateTime existingStart = LocalDateTime.of(2025, 11, 10, 14, 0);
        LocalDateTime existingEnd = existingStart.plusHours(1);
        
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                existingStart, existingEnd, "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime newStart = LocalDateTime.of(2025, 11, 12, 14, 0); // Same week
        LocalDateTime newEnd = newStart.plusHours(1);
        SlotInfo newSlot = createTestSlot(newStart, newEnd, AppointmentType.PHYSIO, true);

        boolean isValid = protocol1Week.checkValidity(newSlot);
        assertTrue("Different appointment types should not conflict with each other", isValid);
    }

    @Test
    public void testMultipleAppointmentsSameType() {
        LocalDateTime first = LocalDateTime.of(2025, 11, 10, 14, 0);
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                first, first.plusHours(1), "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime second = LocalDateTime.of(2025, 11, 24, 14, 0); // Two weeks later
        botDBManager.recordSignup(new Signup(
            "www.test.com",
            new com.autosignup.model.Appointment(
                second, second.plusHours(1), "Test", "Test", "Test", "Test", AppointmentType.MASSAGE
            )
        ));

        LocalDateTime third = LocalDateTime.of(2025, 12, 8, 14, 0); // Another two weeks
        SlotInfo thirdSlot = createTestSlot(third, third.plusHours(1), AppointmentType.MASSAGE, true);

        boolean isValid = protocol1Week.checkValidity(thirdSlot);
        assertTrue("Appointment outside week window of all existing appointments should be valid", isValid);
    }
}
