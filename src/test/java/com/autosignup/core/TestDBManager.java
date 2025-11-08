package com.autosignup.core;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.model.Signup;
import com.autosignup.service.BotDBManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class TestDBManager {
    private BotDBManager botDBManager;
    private static final String DB_PATH = "autosignup.db";

    @Before
    public void setup() {
        // Delete old DB so each test runs on a clean slate
        File dbFile = new File(DB_PATH);
        if (dbFile.exists()) dbFile.delete();

        botDBManager = new BotDBManager("jdbc:sqlite:" + DB_PATH);
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
        boolean inserted = botDBManager.recordSignup(new Signup("www.dummy.ca",
                new Appointment(LocalDateTime.now(), LocalDateTime.now(),
                        "EventName", "summary", "description", "location",
                        AppointmentType.MASSAGE)));
        assertTrue("Insertion should succeed", inserted);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM appointments WHERE site_name = ?")) {
            stmt.setString(1, "ClinicA");
            ResultSet rs = stmt.executeQuery();
            assertTrue("Appointment record should exist", rs.next());
            assertEquals("ClinicA", rs.getString("site_name"));
            assertNotNull("appointment_start_timestamp should not be null", rs.getString("appointment_start_timestamp"));
            assertNotNull("appointment_type should not be null", rs.getString("appointment_type"));
            assertNotNull("signup_timestamp should not be null", rs.getString("signup_timestamp"));
            assertNotNull("appointment_end_timestamp should not be null", rs.getString("appointment_end_timestamp"));
        } catch (SQLException e) {
            fail("Data verification failed: " + e.getMessage());
        }
    }
}
