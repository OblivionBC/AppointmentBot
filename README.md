# Appointment Signup Bot
A bot to sign up for certain appointments.
- Will use Playwright for site navigation/manipulation.
- Use SQLite to store data from the appointments.
- Google SMTP for realtime email notifications.
- Google Calendar API for creation and invitations to meetings.
- YAML to configure the times and days for appointments and websites.
- Containerized in Docker.

# Flow
The application runs on a schedule (default every 5 minutes) and performs the following steps:
1. Navigates to each configured website and locates available appointments.
2. Filters appointments based on configured days/times and policy (e.g., 1/week).
3. Attempts to sign up for the highest-priority matching appointment.
4. On success: creates a Google Calendar event, sends a confirmation email, and stores the appointment in the database.
5. On signup failure: sends an email notification that a slot was found but signup failed.

# Data Model
Currently, a single table:
```sql
appointments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    site_name TEXT,
    signup_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    appointment_start_timestamp DATETIME,
    appointment_end_timestamp DATETIME,
    appointment_type TEXT
);
```

# Running & Deploying

This project supports both local development and Docker deployment. Follow the steps below.

## Prerequisites
- Java 17 (if running locally without Docker)
- Maven 3.8+
- Docker Desktop
- Google Calendar credentials:
  - `src/main/resources/credentials.json`
  - `tokens/StoredCredential` (after authorizing once)

## Local Development

1. **Install Playwright browser dependencies** (one-time):
   ```bash
   mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps"
   ```
2. **Run the app**:
   ```bash
   mvn spring-boot:run
   ```
3. On first run, the app opens a browser for Google OAuth. Sign in and approve access. A refresh token is saved to `tokens/StoredCredential`.

## Build the Executable JAR
```bash
mvn clean package -DskipTests
```
Creates `target/AutoSignupBot-1.0-SNAPSHOT.jar`.

## Build the Docker Image
```bash
docker build -t appointment-bot .
```

## Run the Docker Container

Mount your real credentials and tokens so the container can talk to Google Calendar without re-authorizing:

```powershell
docker run `
  -v <PathToProj>\src\main\resources\credentials.json:/app/src/main/resources/credentials.json:ro `
  -v <PathToProj>\tokens:/app/tokens `
  appointment-bot
```

Notes:
- The first `-v` makes the local `credentials.json` available where the app expects it inside the container.
- The second `-v` mounts the `tokens` directory so OAuth refresh tokens persist.
- Add `-d` to run in detached mode.
- If you want to override properties (e.g., `calendar.attendee`), use environment variables or mount a different `application.yaml`.

## Redeploying
1. Update code/config as needed.
2. `mvn clean package -DskipTests`
3. `docker build -t appointment-bot .`
4. Re-run the `docker run …` command (consider `--rm` to remove old containers automatically).

## Troubleshooting
- **Playwright dependency errors**: ensure the Docker image is built with the latest `Dockerfile`, which installs required libraries.
- **OAuth prompt in Docker**: occurs when `tokens/StoredCredential` isn’t mounted. Mount the directory or copy the file into the container.
- **Wrong calendar times**: set `calendar.timezone` in `application.yaml`.

With these steps, you can develop, build, and deploy the bot locally or on any host with Docker support. Just make sure your OAuth credentials and tokens are available to the container at runtime.