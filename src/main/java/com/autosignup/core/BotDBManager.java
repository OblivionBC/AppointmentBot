package com.autosignup.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BotDBManager {
    private static final Logger logger = LoggerFactory.getLogger(BotDBManager.class);
    private static final String DB_URL = "jdbc:sqlite:autosignup.db";
    private static final Integer APPOINTMENT_PER_WEEKS = 1;
    private Connection connection;

    public BotDBManager() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            createTables();
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void createTables() throws SQLException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("schema.sql");
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder sql = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sql.append(line).append("\n");
            }

            String[] statements = sql.toString().split(";");
            for (String statement : statements) {
                statement = statement.trim();
                if (!statement.isEmpty()) {
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(statement);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to create database tables", e);
            throw new SQLException("Table creation failed", e);
        }
    }

    public boolean recordSignup(String siteName, String slotTime, String appointmentDate) {
        slotTime = slotTime.trim();
        java.time.LocalDateTime date = java.time.LocalDateTime.parse(appointmentDate + " " +  slotTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (hasAppointmentInSameWeek(date)) {
            System.out.println("Appointment already exists this week, skipping insert.");
            return false;
        }

        String sql = "INSERT INTO appointments (site_name, appointment_timestamp) VALUES (?, datetime(?))";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            String formattedTimestamp = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(1, siteName);
            stmt.setString(2, formattedTimestamp);
            stmt.executeUpdate();

            logger.info("Recorded signup: {} - {}", siteName, formattedTimestamp);
        } catch (SQLException e) {
            logger.error("Failed to record signup", e);
        }
        return true;
    }

    public boolean hasAppointmentInSameWeek(LocalDateTime newApptDate) {
        LocalDate startOfWeek = newApptDate.toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6L * APPOINTMENT_PER_WEEKS);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startStr = startOfWeek.atStartOfDay().format(fmt);                // YYYY-MM-DD 00:00:00
        String endStr = endOfWeek.atTime(23, 59, 59).format(fmt);                // YYYY-MM-DD 23:59:59

        String sql = """
        SELECT COUNT(*) 
        FROM appointments
        WHERE strftime('%s', appointment_timestamp) 
              BETWEEN strftime('%s', ?) AND strftime('%s', ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, startStr);
            pstmt.setString(2, endStr);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to check signup for week: {}", e.getMessage(), e);
        }

        return false;
    }

    public void debugDumpAppointments() {
        try (Statement s = connection.createStatement();
             ResultSet rs = s.executeQuery("SELECT id, site_name, appointment_timestamp FROM appointments ORDER BY appointment_timestamp")) {
            System.out.println("---- appointments dump ----");
            while (rs.next()) {
                System.out.println(rs.getInt("id") + " | " + rs.getString("site_name") +
                        " | '" + rs.getString("appointment_timestamp") + "'");
            }
            System.out.println("---------------------------");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database connection", e);
        }
    }
}
