CREATE TABLE IF NOT EXISTS appointments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    site_name TEXT,
    signup_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    appointment_start_timestamp DATETIME,
    appointment_end_timestamp DATETIME,
    appointment_type TEXT,
);

CREATE INDEX IF NOT EXISTS idx_appointment_timestamp ON appointments(appointment_timestamp);
