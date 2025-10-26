# Appointment Signup Bot
A bot to sign up for certain appointments.
- Will use playwright for site navigation/manipulation.
- Use SQLite to store data from the appointments.
- Google SMTP for realtime Email notifications.
- Google Calender API for creation and invitations to meetings.
- YAML to configure the times and days for appointments, and websites
- Containerize in Docker

# Flow
Application will be containerized in Docker, and set to do a run every 5 minutes.
It will go to each configured website, navigate to the appointments, then search
for appointments in configured days and times.
The policy for appointment frequency will also be applied for appointment filtering , 
such as 1/week, or 2/week, etc.
If found, it will sign up for the appointment with the highest priority, and upon success, will
create a google calendar event, send an email with the link and time, and cache the appointment in the database.
If it fails the sign up but found the appointment, it will send an email saying it could 
not sign up, but there is an appointment available.

# Data Model
Literally just 1 table

## Appointment
Has the Date time of signup, Date Time of the appointment, 
and the website string URL it signed up on