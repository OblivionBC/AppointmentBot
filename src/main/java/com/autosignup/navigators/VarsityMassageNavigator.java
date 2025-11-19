package com.autosignup.navigators;

import com.autosignup.model.Appointment;
import com.autosignup.model.AppointmentType;
import com.autosignup.model.SlotInfo;
import com.autosignup.model.WebsiteSpecs;
import com.autosignup.model.protocol.ProtocolFactory;
import com.autosignup.service.BotDBManager;
import com.autosignup.service.ConfigLoaderService;
import com.autosignup.util.PlaywrightWrapper;
import com.microsoft.playwright.ElementHandle;
import jakarta.annotation.PostConstruct;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VarsityMassageNavigator extends WebsiteNavigator {
    private static final Logger logger = LoggerFactory.getLogger(VarsityMassageNavigator.class);
    private final ConfigLoaderService configLoader;
    
    @Value("${navigators.massage.enabled:true}")
    private boolean enabled;
    
    @Setter
    @Value("${navigators.massage.urls}")
    private List<String> urls;
    
    public VarsityMassageNavigator(ProtocolFactory factory, PlaywrightWrapper playwright, ConfigLoaderService configLoader, BotDBManager botDBManager) {
        super(factory, AppointmentType.MASSAGE, playwright, botDBManager);
        this.protocol = factory.createTypeWeeksProtocol(1);
        this.configLoader = configLoader;
    }

    @PostConstruct
    public void loadConfig() {
        this.navigatorConfig = configLoader.getNavigatorConfig("massage_navigator");
        
        for (String url : urls) {
            WebsiteSpecs specs = new WebsiteSpecs(protocol, url, AppointmentType.MASSAGE);
            websites.add(specs);
            logger.info("Loaded URL: {}", url);
        }
        
        logger.info("VarsityMassageNavigator initialized with {} sites and {} slot preferences", 
                   websites.size(), navigatorConfig.slots().size());
    }

    @Override
    public List<Appointment> navigate() {
        List<Appointment> appointments = new ArrayList<>();
        for (WebsiteSpecs specs : websites) {
            List<SlotInfo> slots = runPlaywright(specs);
            logger.info("Found {} slots for {}", slots.size(), specs.url());
            
            List<SlotInfo> validSlots = slots.stream()
                .filter(slot -> slot.available() && protocol.checkValidity(slot))
                .toList();
            
            logger.info("After protocol filtering: {} valid slots", validSlots.size());
            
            for (SlotInfo slot : validSlots) {
                Appointment appointment = slotInfoToAppointment(slot);
                appointments.add(appointment);
                appointmentToSlotMap.put(appointment, slot);
                logger.info("Created appointment: {}", appointment);
            }
        }
        return appointments;
    }

    public List<SlotInfo> runPlaywright(WebsiteSpecs specs) {
        List<SlotInfo> foundSlots = new ArrayList<>();
        
        try {
            playwright.navigateTo(specs.url(), 3);
            
            List<ElementHandle> slots = playwright.findSlots(getSlotSelectors());
            if (slots.isEmpty()) {
                logger.info("No slots found on site: {}", specs.url());
                return foundSlots;
            }
            
            logger.info("Found {} potential slots", slots.size());

            foundSlots = parseSlotsWithUrl(slots, specs.url());
            
            logger.info("Successfully parsed {} slots from {}", foundSlots.size(), specs.url());
            
        } catch (Exception e) {
            logger.error("Error during navigation for site {}: {}", specs.url(), e.getMessage(), e);
        }
        
        return foundSlots;
    }
    
    private List<SlotInfo> parseSlotsWithUrl(List<ElementHandle> slots, String url) {
        List<SlotInfo> foundSlots = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) {
            ElementHandle slot = slots.get(i);
            SlotInfo slotInfo = parseSlot(slot, url);
            if (slotInfo != null) {
                foundSlots.add(slotInfo);
                logger.debug("Slot {}: Day={}, Time={}, Start={}, End={}, Available={}, URL={}",
                        i + 1, slotInfo.day(), slotInfo.time(),
                        slotInfo.start(), slotInfo.end(), slotInfo.available(), slotInfo.sourceUrl());
            }
        }
        return foundSlots;
    }

    protected Map<String, String> getSlotSelectors() {
        return Map.of(
                "slot_container", ".first-row",
                "confirmation_modal", ".modal, .popup, .confirmation"
        );
    }

    protected SlotInfo parseSlot(ElementHandle element, String sourceUrl) {
        try {
            List<ElementHandle> timeElements = element.querySelectorAll("time");
            if (timeElements.isEmpty()) {
                logger.debug("No time elements found in slot");
                return null;
            }
            
            String timeText = playwright.getElementText(timeElements.get(0));
            String time = convertTo24Hour(timeText);
            if (time == null) {
                logger.debug("Could not convert time: {}", timeText);
                return null;
            }
            
            String jsCode = "el => { " +
                    "let dateBanners = Array.from(document.querySelectorAll('.date-banner')); " +
                    "if (dateBanners.length === 0) return null; " +
                    "let rect = el.getBoundingClientRect(); " +
                    "let matchingBanner = null; " +
                    "for (let banner of dateBanners) { " +
                    "let bannerRect = banner.getBoundingClientRect(); " +
                    "if (bannerRect.top < rect.top) { matchingBanner = banner; } " +
                    "} " +
                    "return matchingBanner ? matchingBanner.textContent : null; " +
                    "}";
            Object dateInfo = element.evaluate(jsCode);
            
            if (dateInfo == null) {
                logger.debug("Could not find date banner");
                return null;
            }
            
            String dateText = dateInfo.toString();
            String day = extractDayFromText(dateText);
            LocalDate appointmentDate = extractDateFromText(dateText);
            
            if (day == null || appointmentDate == null) {
                logger.debug("Could not extract day or date from: {}", dateText);
                return null;
            }
            
            LocalDateTime start = parseTimeToLocalDateTime(appointmentDate, time);
            if (start == null) {
                logger.debug("Could not create start LocalDateTime from date: {} and time: {}", appointmentDate, time);
                return null;
            }
            
            LocalDateTime end = start.plusHours(1);
            
            boolean available = isSlotAvailable(element);
            
            return new SlotInfo(element, day, time, start, end, available, appointmentType, sourceUrl);
            
        } catch (Exception e) {
            logger.debug("Failed to parse slot: {}", e.getMessage());
            return null;
        }
    }
    
    private String convertTo24Hour(String timeText) {
        try {
            timeText = timeText.trim().toUpperCase();
            
            Pattern pattern = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM)");
            Matcher matcher = pattern.matcher(timeText);
            
            if (matcher.find()) {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                String ampm = matcher.group(3);
                
                if (ampm.equals("PM") && hour != 12) {
                    hour += 12;
                } else if (ampm.equals("AM") && hour == 12) {
                    hour = 0;
                }
                
                return String.format("%02d:%02d", hour, minute);
            }
            
            return null;
        } catch (Exception e) {
            logger.debug("Error converting time: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractDayFromText(String text) {
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String day : days) {
            if (text.contains(day)) {
                return day;
            }
        }
        return null;
    }
    
    private LocalDate extractDateFromText(String text) {
        try {
            Pattern pattern = Pattern.compile("(January|February|March|April|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{1,2}),?\\s+(\\d{4})");
            Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {
                String month = matcher.group(1);
                String day = matcher.group(2);
                String year = matcher.group(3);
                
                String fullMonth = convertToFullMonth(month);
                String dateStr = fullMonth + " " + day + ", " + year;
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy");
                return LocalDate.parse(dateStr, formatter);
            }
            
            return null;
        } catch (Exception e) {
            logger.debug("Error extracting date: {}", e.getMessage());
            return null;
        }
    }
    
    private String convertToFullMonth(String month) {
        return switch (month) {
            case "Jan" -> "January";
            case "Feb" -> "February";
            case "Mar" -> "March";
            case "Apr" -> "April";
            case "Jun" -> "June";
            case "Jul" -> "July";
            case "Aug" -> "August";
            case "Sep" -> "September";
            case "Oct" -> "October";
            case "Nov" -> "November";
            case "Dec" -> "December";
            default -> month;
        };
    }
    
    private boolean isSlotAvailable(ElementHandle element) {
        try {
            List<ElementHandle> signupButtons = element.querySelectorAll("button[data-i18n='_SignUp_'], button:has-text('Sign Up')");
            return !signupButtons.isEmpty();
        } catch (Exception e) {
            logger.debug("Error checking availability: {}", e.getMessage());
            return false;
        }
    }
    
    private LocalDateTime parseTimeToLocalDateTime(LocalDate date, String time24Hour) {
        try {
            String[] parts = time24Hour.split(":");
            if (parts.length != 2) {
                return null;
            }
            
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            LocalTime localTime = LocalTime.of(hour, minute);
            return LocalDateTime.of(date, localTime);
        } catch (Exception e) {
            logger.debug("Error parsing time to LocalDateTime: {}", e.getMessage());
            return null;
        }
    }
    
    @Override
    public boolean signup(Appointment appointment) {
        try {
            SlotInfo slot = appointmentToSlotMap.get(appointment);
            if (slot == null) {
                logger.error("No SlotInfo found for appointment: {}", appointment);
                return false;
            }
            
            String slotUrl = slot.sourceUrl();
            if (slotUrl != null && !slotUrl.isEmpty()) {
                logger.info("Navigating to slot's source URL: {}", slotUrl);
                playwright.navigateTo(slotUrl, 3);
                playwright.getPage().waitForTimeout(2000);
            } else {
                logger.warn("Slot has no source URL, attempting signup on current page");
            }
            
            List<ElementHandle> allSlots = playwright.findSlots(getSlotSelectors());
            if (allSlots.isEmpty()) {
                logger.error("No slots found on page after navigation");
                playwright.takeScreenshot("signup_no_slots_after_nav");
                return false;
            }
            
            ElementHandle slotElement = null;
            for (ElementHandle candidateSlot : allSlots) {
                SlotInfo candidateInfo = parseSlot(candidateSlot, slotUrl);
                if (candidateInfo != null && 
                    candidateInfo.start().equals(slot.start()) &&
                    candidateInfo.day().equals(slot.day()) &&
                    candidateInfo.time().equals(slot.time())) {
                    slotElement = candidateSlot;
                    logger.info("Re-found matching slot: {} at {}", candidateInfo.day(), candidateInfo.time());
                    break;
                }
            }
            
            if (slotElement == null) {
                logger.error("Could not re-locate slot for appointment: {}", appointment);
                playwright.takeScreenshot("signup_slot_not_found");
                return false;
            }
            
            var userConfig = configLoader.getSignupUserConfig();
            if (userConfig == null) {
                logger.error("No signup user configuration available");
                return false;
            }
            
            logger.info("Starting signup process for appointment: {} with user: {} {}", 
                       appointment, userConfig.firstName(), userConfig.lastName());
            
            List<ElementHandle> signupButtons = slotElement.querySelectorAll("button[data-i18n='_SignUp_'], button:has-text('Sign Up')");
            if (signupButtons.isEmpty()) {
                logger.error("No signup button found for slot");
                playwright.takeScreenshot("signup_no_button");
                return false;
            }
            
            playwright.clickElement(signupButtons.get(0));
            logger.info("Clicked signup button");
            
            boolean formAppeared = playwright.waitForModal("form, .modal, .signup-form", 5000);
            if (!formAppeared) {
                logger.warn("Form modal did not appear, continuing anyway");
            }
            playwright.getPage().waitForTimeout(2000);
            
            try {
                playwright.fillFormField("input[name='email'], input[type='email'], input[placeholder*='Email'], input[id*='email']", 
                                       userConfig.email());
                logger.info("Filled email field");
            } catch (Exception e) {
                logger.error("Failed to fill email field: {}", e.getMessage());
                playwright.takeScreenshot("signup_email_failed");
                return false;
            }
            
            playwright.takeScreenshot("signup_before_submit");
            
            try {
                playwright.clickSelector("button[type='submit'], button:has-text('Submit'), button:has-text('Confirm'), button:has-text('Sign Up')");
                playwright.clickSelector("#confirm_button");
                playwright.clickSelector("button[data-trackelem='_FinishSignUp_']");
                boolean success = playwright.isElementVisible(".success, .confirmation, [class*='success']") ||
                                 playwright.isElementVisible("*:has-text('Thank you'), *:has-text('Confirmed'), *:has-text('Success')");

                playwright.takeScreenshot("signup_after_submit");
                
                if (success) {
                    logger.info("Successfully signed up for appointment: {}", appointment);
                } else {
                    logger.warn("Signup completed but no success confirmation found");
                }
                return true;
            } catch (Exception e) {
                logger.error("Failed to click submit button: {}", e.getMessage());
                playwright.takeScreenshot("signup_submit_error");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error during signup: {}", e.getMessage(), e);
            playwright.takeScreenshot("signup_general_error");
            return false;
        }
    }
    
    @Override
    public List<Appointment> runAll() {
        if (!enabled) {
            logger.info("VarsityMassageNavigator is disabled, skipping execution");
            return new ArrayList<>();
        }
        return super.runAll();
    }
    
}