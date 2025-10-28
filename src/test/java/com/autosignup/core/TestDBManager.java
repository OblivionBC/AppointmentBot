package com.autosignup.core;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

public class TestDBManager {
    private BotDBManager botDBManager;
    private static final String DB_PATH = "autosignup.db";

    @Before
    public void setup() {
        // Delete old DB so each test runs on a clean slate
        File dbFile = new File(DB_PATH);
        if (dbFile.exists()) dbFile.delete();

        botDBManager = new BotDBManager();
    }

    @After
    public void teardown() {
        botDBManager.close();
        File dbFile = new File(DB_PATH);
        if (dbFile.exists()) dbFile.delete();
    }

    @Test
    public void testconstructor() {
        // Test that the table is created on construction
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH)) {
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getTables(null, null, "appointments", null);
            assertTrue("appointments table should exist after construction", rs.next());
        } catch (SQLException e) {
            fail("Database check failed: " + e.getMessage());
        }
    }

    @Test
    public void testDataRecord() {
        // Add an appointment, make sure it’s in the DB with correct data model
        boolean inserted = botDBManager.recordSignup("ClinicA", "10:00:00", "2025-10-20");
        assertTrue("Insertion should succeed", inserted);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM appointments WHERE site_name = ?")) {
            stmt.setString(1, "ClinicA");
            ResultSet rs = stmt.executeQuery();

            assertTrue("Appointment record should exist", rs.next());
            assertEquals("ClinicA", rs.getString("site_name"));
            assertNotNull("Timestamp should not be null", rs.getString("appointment_timestamp"));
        } catch (SQLException e) {
            fail("Data verification failed: " + e.getMessage());
        }
    }

    @Test
    public void testValidMultiSignup() {
        // Three different weeks: ensures that week logic passes correctly
        boolean first = botDBManager.recordSignup("ClinicB", "09:00:00", "2025-10-03"); // Friday
        boolean second = botDBManager.recordSignup("ClinicB", "09:00:00", "2025-10-06"); // Next Monday (different week)
        boolean third = botDBManager.recordSignup("ClinicB", "09:00:00", "2025-10-13"); // Another Monday, new week

        assertTrue("Friday signup should succeed", first);
        assertTrue("Next Monday (new week) should succeed", second);
        assertTrue("Another Monday (another new week) should succeed", third);
    }

    @Test
    public void testInvalidMultiSignup() {
        // Case 1: Monday then Friday (same week → reject second)
        boolean monday = botDBManager.recordSignup("ClinicC", "09:00:00", "2025-10-20");
        boolean friday = botDBManager.recordSignup("ClinicC", "09:00:00", "2025-10-24");

        assertTrue("First Monday insert should succeed", monday);
        assertFalse("Friday insert in same week should fail", friday);

        // Case 2: Friday then Monday (different week → accept Monday)
        boolean friday2 = botDBManager.recordSignup("ClinicC", "09:00:00", "2025-10-03");
        boolean monday2 = botDBManager.recordSignup("ClinicC", "09:00:00", "2025-10-06");

        assertTrue("Friday insert should succeed", friday2);
        assertTrue("Following Monday insert should succeed (different week)", monday2);
    }
}
