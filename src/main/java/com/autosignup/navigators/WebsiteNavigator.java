package com.autosignup.navigators;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.model.SlotInfo;
import com.autosignup.model.WebsiteSpecs;
import com.autosignup.model.Signup;
import com.autosignup.model.config.NavigatorConfig;
import com.autosignup.model.config.SlotConfig;
import com.autosignup.model.protocol.ProtocolFactory;
import com.autosignup.model.protocol.SignupProtocol;
import com.autosignup.service.BotDBManager;
import com.autosignup.service.EmailService;
import com.autosignup.util.PlaywrightWrapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public abstract class WebsiteNavigator {
    private static final Logger logger = LoggerFactory.getLogger(WebsiteNavigator.class);
    
    @Getter
    protected final List<WebsiteSpecs> websites;
    protected final ProtocolFactory factory;
    @Getter
    protected SignupProtocol protocol = null;
    protected final AppointmentType appointmentType;
    protected PlaywrightWrapper playwright;
    protected NavigatorConfig navigatorConfig;
    protected Map<Appointment, SlotInfo> appointmentToSlotMap = new HashMap<>();
    protected final BotDBManager botDBManager;
    @Setter
    protected EmailService emailService;

    public WebsiteNavigator(ProtocolFactory factory, AppointmentType appointmentType, PlaywrightWrapper playwright, BotDBManager botDBManager) {
        this.factory = factory;
        this.appointmentType = appointmentType;
        this.websites = new ArrayList<>();
        this.playwright = playwright;
        this.botDBManager = botDBManager;
    }

    public abstract List<Appointment> navigate();

    public List<Appointment> filterForConfig(List<Appointment> possibleAppointments) {
        if (navigatorConfig == null || navigatorConfig.slots().isEmpty()) {
            logger.warn("No navigator config available for filtering");
            return new ArrayList<>();
        }

        List<Appointment> filtered = new ArrayList<>();

        for (Appointment appointment : possibleAppointments) {
            for (var slotConfig : navigatorConfig.slots()) {
                if (matchesSlotConfig(appointment, slotConfig)) {
                    filtered.add(appointment);
                    logger.info("Appointment matches config - Day: {}, Time: {} - {}",
                            slotConfig.day(), slotConfig.start(), slotConfig.end());
                    break;
                }
            }
        }

        logger.info("Filtered {} appointments from {} total", filtered.size(), possibleAppointments.size());
        return filtered;
    }

    public abstract boolean signup(Appointment appointment);

    public List<Appointment> runFlow() {
        logger.info("Starting runFlow for {}", this.getClass().getSimpleName());
        List<Appointment> signedUpAppointments = new ArrayList<>();
        
        try {
            appointmentToSlotMap.clear();
            List<Appointment> allAppointments = navigate();
            logger.info("Step 1: Found {} total appointments", allAppointments.size());
            
            if (allAppointments.isEmpty()) {
                logger.info("No appointments found, ending flow");
                return signedUpAppointments;
            }
            
            List<Appointment> filteredAppointments = filterForConfig(allAppointments);
            logger.info("Step 2: Filtered to {} appointments matching config", filteredAppointments.size());
            
            if (filteredAppointments.isEmpty()) {
                logger.info("No appointments match configuration, ending flow");
                return signedUpAppointments;
            }
            
            List<Appointment> remainingAppointments = new ArrayList<>(filterByPriority(filteredAppointments, 1));
            logger.info("Step 3: Found {} priority 1 appointments", remainingAppointments.size());
            
            if (remainingAppointments.isEmpty()) {
                logger.info("No priority 1 appointments found, ending flow");
                return signedUpAppointments;
            }
            
            Appointment firstAppointment = remainingAppointments.get(0);
            logger.info("Step 4: Attempting signup for first priority 1 appointment: {}", firstAppointment);
            
            if (signup(firstAppointment)) {
                signedUpAppointments.add(firstAppointment);
                recordSignup(firstAppointment);
                remainingAppointments.remove(0);
                logger.info("Successfully signed up for first appointment, {} remaining", remainingAppointments.size());
            } else {
                logger.warn("Failed to sign up for first appointment");
                if (emailService != null) {
                    emailService.sendErrorEmail(
                        this.getClass().getSimpleName(),
                        firstAppointment.toString(),
                        "Signup method returned false - unable to complete signup process"
                    );
                }
                remainingAppointments.remove(0);
            }
            
            int iteration = 1;
            while (!remainingAppointments.isEmpty()) {
                logger.info("Step 5.{}: Processing remaining appointments, {} left", iteration, remainingAppointments.size());
                
                List<Appointment> refiltered = filterForConfig(remainingAppointments);
                logger.info("Step 5.{}.a: After config filter: {} appointments", iteration, refiltered.size());
                
                if (refiltered.isEmpty()) {
                    logger.info("No remaining appointments match config, ending flow");
                    break;
                }
                
                List<Appointment> validAppointments = new ArrayList<>();
                for (Appointment appointment : refiltered) {
                    SlotInfo slot = appointmentToSlotMap.get(appointment);
                    if (slot != null && protocol.checkValidity(slot)) {
                        validAppointments.add(appointment);
                    }
                }
                
                logger.info("Step 5.{}.b: After protocol filter: {} valid appointments", iteration, validAppointments.size());
                
                if (validAppointments.isEmpty()) {
                    logger.info("No remaining appointments passed protocol check, ending flow");
                    break;
                }
                
                Appointment nextAppointment = validAppointments.get(0);
                logger.info("Step 5.{}.c: Attempting signup for appointment: {}", iteration, nextAppointment);
                
                if (signup(nextAppointment)) {
                    signedUpAppointments.add(nextAppointment);
                    recordSignup(nextAppointment);
                    logger.info("Successfully signed up for appointment");
                } else {
                    logger.warn("Failed to sign up for appointment");
                    if (emailService != null) {
                        emailService.sendErrorEmail(
                            this.getClass().getSimpleName(),
                            nextAppointment.toString(),
                            "Signup method returned false - unable to complete signup process"
                        );
                    }
                }
                
                remainingAppointments.remove(nextAppointment);
                iteration++;
            }
            
            logger.info("runFlow completed: Successfully signed up for {} appointments", signedUpAppointments.size());
            
        } catch (Exception e) {
            logger.error("Error during runFlow: {}", e.getMessage(), e);
            if (emailService != null) {
                emailService.sendErrorEmail(
                    this.getClass().getSimpleName(),
                    "Error during navigation/signup flow",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                );
            }
        }
        
        return signedUpAppointments;
    }


    public List<Appointment> runAll() {
        return runFlow();
    }


    protected Appointment slotInfoToAppointment(SlotInfo slot) {
        String location = "UBC";
        return new Appointment(
            slot.start(),
            slot.end(),
            slot.appointmentType().toString() + " Appointment",
            slot.appointmentType().toString() + " : " + location,
            "Booked via UBC Massage Bot",
            location,
            slot.appointmentType()
        );
    }


    protected boolean matchesSlotConfig(Appointment appointment, SlotConfig slotConfig) {
        try {
            String appointmentDay = appointment.start().getDayOfWeek().toString().substring(0, 3);
            if (!appointmentDay.equalsIgnoreCase(slotConfig.day().substring(0, 3))) {
                return false;
            }
            
            LocalTime slotStart = LocalTime.parse(slotConfig.start());
            LocalTime slotEnd = LocalTime.parse(slotConfig.end());
            
            LocalTime appointmentStartTime = appointment.start().toLocalTime();
            LocalTime appointmentEndTime = appointment.end().toLocalTime();

            return (!appointmentStartTime.isBefore(slotStart) && appointmentStartTime.isBefore(slotEnd)) ||
                              (appointmentEndTime.isAfter(slotStart) && !appointmentEndTime.isAfter(slotEnd)) ||
                              (!appointmentStartTime.isAfter(slotStart) && !appointmentEndTime.isBefore(slotEnd));
        } catch (Exception e) {
            logger.debug("Error matching slot config: {}", e.getMessage());
            return false;
        }
    }

    protected List<Appointment> filterByPriority(List<Appointment> appointments, int targetPriority) {
        if (targetPriority == 1) {
            return new ArrayList<>(appointments);
        }
        return new ArrayList<>();
    }

    private void recordSignup(Appointment appointment) {
        try {
            SlotInfo slot = appointmentToSlotMap.get(appointment);
            String sourceUrl = slot != null ? slot.sourceUrl() : "unknown";
            botDBManager.recordSignup(new Signup(sourceUrl, appointment));
        } catch (Exception e) {
            logger.warn("Failed to record signup for appointment {}: {}", appointment, e.getMessage());
        }
    }
}