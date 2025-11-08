package com.autosignup.service;

import com.autosignup.model.Appointment;
import com.autosignup.model.Signup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BotDBManager {
    private static final Logger logger = LoggerFactory.getLogger(BotDBManager.class);
    @Value("${db.url}")
    private String DB_URL;
    private static final Integer APPOINTMENT_PER_WEEKS = 1;
    @Getter
    private Connection connection;

    public BotDBManager() {
    }

    public BotDBManager(String dbUrl) {
        this.DB_URL = dbUrl;
        initializeDatabase();
    }

    @PostConstruct
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

    public ResultSet runQuery(String sql, List<Object> params) throws SQLException {
        PreparedStatement stmt = connection.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
        return stmt.executeQuery();
    }

    public boolean recordSignup(Signup signup) {
        Appointment appointment = signup.Appointment();

        String sql = "INSERT INTO appointments (site_name, appointment_start_timestamp, appointment_end_timestamp, appointment_type) VALUES (?, datetime(?), datetime(?), ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            String formattedStartTimestamp = (appointment.start().format(DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")));
            String formattedEndTimestamp = (appointment.end().format(DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")));

            stmt.setString(1, signup.URL());
            stmt.setString(2, formattedStartTimestamp);
            stmt.setString(3, formattedEndTimestamp);
            stmt.setString(4, appointment.appointmentType().toString());
            stmt.executeUpdate();

            logger.info("Recorded signup: {} - {}", signup.URL(), formattedStartTimestamp);
        } catch (SQLException e) {
            logger.error("Failed to record signup", e);
        }
        return true;
    }

    @PreDestroy
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
