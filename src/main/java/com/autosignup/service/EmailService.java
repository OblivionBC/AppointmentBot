package com.autosignup.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.*;
import javax.mail.internet.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.Collectors;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    @Value("${smtp.host}")
    private String smtpHost;
    @Value("${smtp.port}")
    private int smtpPort;
    @Value("${smtp.user}")
    private String smtpUser;
    @Value("${smtp.password}")
    private String smtpPass;
    @Value("${smtp.from-email}")
    private String smtpFromEmail;
    @Value("${smtp.to-email}")
    private String smtpToEmail;

    private Session session;

    @PostConstruct
    private void initializeSession() {
        try {
            if (smtpHost == null || smtpPort <= 0 || smtpUser == null || smtpPass == null) {
                logger.warn("SMTP configuration incomplete - email notifications will be disabled");
                logger.warn("Missing: host={}, port={}, user={}, pass={}",
                        smtpHost != null, smtpPort > 0, smtpUser != null, smtpPass != null);
                session = null;
                return;
            }

            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", smtpHost);

            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPass);
                }
            });
            logger.info("Email service initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize email service", e);
            session = null;
            logger.warn("Email service disabled due to initialization failure");
        }
    }

    private void sendEmail(String body) {
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFromEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(smtpToEmail));
            message.setSubject("Appointment Scheduled with AutosignupBot");
            message.setContent(body, "text/html; charset=utf-8");

            Transport.send(message);
            logger.info("Email sent successfully: {}", "Appointment Scheduled with AutosignupBot");

        } catch (Exception e) {
            logger.error("Failed to send email: {}", e.getMessage());
        }
    }

    public void sendEmailWithCalendarEvent(Event event) {
        try {
            String htmlBody = buildEmailFromTemplate(event);
            sendEmail(htmlBody);
            logger.info("Confirmation email sent with calendar link");
        } catch (Exception gcalException) {
            logger.warn("Could not use Google Calendar API: {}", gcalException.getMessage());
            logger.info("Falling back to ICS email attachment method...");
        }
    }
    
    private String buildEmailFromTemplate(Event event) {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("email-template.html");
            if (inputStream == null) {
                logger.warn("Email template not found, using fallback");
                return buildFallbackEmail(event);
            }
            
            String template = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            
            String eventSummary = event.getSummary() != null ? event.getSummary() : "Your Appointment";
            String eventId = event.getId() != null ? event.getId() : "N/A";
            String calendarLink = event.getHtmlLink() != null ? event.getHtmlLink() : "#";
            
            String eventStart = formatEventTime(event.getStart());
            
            String location = event.getLocation() != null ? event.getLocation() : "TBD";
            
            String htmlBody = template
                    .replace("{{EVENT_SUMMARY}}", eventSummary)
                    .replace("{{EVENT_START}}", eventStart)
                    .replace("{{EVENT_LOCATION}}", location)
                    .replace("{{EVENT_ID}}", eventId)
                    .replace("{{CALENDAR_LINK}}", calendarLink);
            
            return htmlBody;
            
        } catch (Exception e) {
            logger.error("Error building email from template: {}", e.getMessage());
            return buildFallbackEmail(event);
        }
    }
    
    private String formatEventTime(EventDateTime eventDateTime) {
        if (eventDateTime == null) {
            return "TBD";
        }
        
        try {
            if (eventDateTime.getDateTime() != null) {
                String dateTimeStr = eventDateTime.getDateTime().toString();
                return dateTimeStr.replace("T", " at ").substring(0, 16);
            } else if (eventDateTime.getDate() != null) {
                return eventDateTime.getDate().toString();
            }
        } catch (Exception e) {
            logger.warn("Error formatting event time: {}", e.getMessage());
        }
        
        return "TBD";
    }
    
    private String buildFallbackEmail(Event event) {
        return String.format(
                "<html><body style='font-family: Arial, sans-serif; padding: 20px;'>" +
                "<h2 style='color: #667eea;'>Appointment Confirmed</h2>" +
                "<p>Your appointment has been successfully scheduled by <strong>AutoSignup Bot</strong>.</p>" +
                "<p><strong>Event:</strong> %s</p>" +
                "<p><strong>Event ID:</strong> %s</p>" +
                "<p><a href='%s' style='display: inline-block; padding: 10px 20px; background-color: #667eea; color: white; text-decoration: none; border-radius: 5px;'>View in Calendar</a></p>" +
                "</body></html>",
                event.getSummary() != null ? event.getSummary() : "Your Appointment",
                event.getId() != null ? event.getId() : "N/A",
                event.getHtmlLink() != null ? event.getHtmlLink() : "#"
        );
    }
}