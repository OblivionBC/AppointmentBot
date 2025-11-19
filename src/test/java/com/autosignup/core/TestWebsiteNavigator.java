package com.autosignup.core;

import com.autosignup.model.Appointment;
import com.autosignup.model.config.*;
import com.autosignup.model.protocol.ProtocolFactory;
import com.autosignup.navigators.VarsityChiroNavigator;
import com.autosignup.navigators.VarsityMassageNavigator;
import com.autosignup.service.BotDBManager;
import com.autosignup.service.ConfigLoaderService;
import com.autosignup.util.PlaywrightWrapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TestWebsiteNavigator {
    private static final Logger logger = LoggerFactory.getLogger(TestWebsiteNavigator.class);
    private static final String TEST_DB_PATH = "test-navigator.db";
    
    private BotDBManager botDBManager;
    private ProtocolFactory protocolFactory;
    private PlaywrightWrapper playwright;
    private TestConfigLoaderService configLoader;
    private VarsityMassageNavigator massageNavigator;
    private VarsityChiroNavigator chiroNavigator;

    @Before
    public void setup() {
        logger.info("Setting up test environment");
        
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        botDBManager = new BotDBManager("jdbc:sqlite:" + TEST_DB_PATH);
        protocolFactory = new ProtocolFactory(botDBManager);
        
        playwright = new PlaywrightWrapper();
        
        configLoader = new TestConfigLoaderService();
        configLoader.loadConfig();
        
        massageNavigator = new VarsityMassageNavigator(protocolFactory, playwright, configLoader, botDBManager);
        massageNavigator.loadConfig();
        
        chiroNavigator = new VarsityChiroNavigator(protocolFactory, playwright, configLoader, botDBManager);
        chiroNavigator.loadConfig();
        
        logger.info("Test environment setup complete");
    }

    @After
    public void teardown() {
        logger.info("Tearing down test environment");
        
        if (playwright != null) {
            playwright.close();
        }
        
        if (botDBManager != null) {
            botDBManager.close();
        }
        
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        
        logger.info("Test environment teardown complete");
    }

    @Test
    public void testMassageNavigatorInitialization() {
        logger.info("Testing VarsityMassageNavigator initialization");
        
        assertNotNull("Massage navigator should be initialized", massageNavigator);

        assertFalse("Massage navigator should have at least 1 website configured", massageNavigator.getWebsites().isEmpty());
        
        logger.info("VarsityMassageNavigator initialized with {} sites", massageNavigator.getWebsites().size());
        
        assertNotNull("Massage navigator should have a protocol", massageNavigator.getProtocol());
        
        logger.info("Massage navigator initialization test passed");
    }

    @Test
    public void testChiroNavigatorInitialization() {
        logger.info("Testing VarsityChiroNavigator initialization");
        
        assertNotNull("Chiro navigator should be initialized", chiroNavigator);

        assertFalse("Chiro navigator should have at least 1 website configured", chiroNavigator.getWebsites().isEmpty());
        
        logger.info("VarsityChiroNavigator initialized with {} sites", chiroNavigator.getWebsites().size());
        
        assertNotNull("Chiro navigator should have a protocol", chiroNavigator.getProtocol());
        
        logger.info("Chiro navigator initialization test passed");
    }

    @Test
    public void testMassageNavigatorEndToEndSlotDetection() {
        logger.info("=== Starting Massage Navigator End-to-End Slot Detection Test ===");
        
        List<Appointment> appointments = massageNavigator.navigate();
        
        logger.info("Massage Navigator E2E Test Results:");
        logger.info("Total appointments found: {}", appointments.size());
        
        if (appointments.isEmpty()) {
            logger.warn("No appointments found - this might be expected if no slots are available");
            logger.warn("Check the test URL in test-config.yaml to ensure it has available slots");
        } else {
            logger.info("Successfully detected {} appointments:", appointments.size());
            for (int i = 0; i < appointments.size(); i++) {
                Appointment apt = appointments.get(i);
                logger.info("  Appointment {}: {} - {} to {}", 
                    i + 1, apt.summary(), apt.start(), apt.end());
            }
        }
        
        assertNotNull("Navigate should return a list (not null)", appointments);
        
        logger.info("=== Massage Navigator E2E Test Complete ===");
    }

    @Test
    public void testChiroNavigatorEndToEndStructure() {
        logger.info("=== Starting Chiro Navigator End-to-End Structure Test ===");
        
        List<Appointment> appointments = chiroNavigator.navigate();
        
        logger.info("Chiro Navigator E2E Test Results:");

        assertNotNull("Navigate should return a list (not null)", appointments);
        assertEquals("Chiro navigator should return empty list until implemented", 0, appointments.size());
        
        logger.info("Chiro navigator structure test passed - ready for implementation");
        logger.info("=== Chiro Navigator E2E Test Complete ===");
    }

     // Helper class to load test configuration
    private static class TestConfigLoaderService extends ConfigLoaderService {
        private AppConfig appConfig;

        @Override
        public void loadConfig() {
            try {
                Yaml yaml = new Yaml();
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-config.yaml");
                
                if (inputStream == null) {
                    throw new RuntimeException("test-config.yaml not found in resources");
                }
                
                Map<String, Object> data = yaml.load(inputStream);
                
                int checkInterval = (int) data.get("check_interval_seconds");
                
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> navigatorsData = 
                    (Map<String, Map<String, Object>>) data.get("navigators");
                
                Map<String, NavigatorConfig> navigators = new java.util.HashMap<>();
                
                for (Map.Entry<String, Map<String, Object>> entry : navigatorsData.entrySet()) {
                    String navigatorName = entry.getKey();
                    Map<String, Object> navigatorData = entry.getValue();
                    
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> slotsData = 
                        (List<Map<String, Object>>) navigatorData.get("slots");
                    
                    List<SlotConfig> slots = new ArrayList<>();
                    
                    if (slotsData != null) {
                        for (Map<String, Object> slotData : slotsData) {
                            String day = (String) slotData.get("day");
                            String start = (String) slotData.get("start");
                            String end = (String) slotData.get("end");
                            
                            slots.add(new SlotConfig(day, start, end));
                        }
                    }
                    
                    navigators.put(navigatorName, new NavigatorConfig(slots));
                }
                
                appConfig = new AppConfig(checkInterval, navigators, new SignupUserConfig("test", "user", "test@gmail.com", "77777"));
                
                logger.info("Successfully loaded test config with {} navigators", navigators.size());
                for (Map.Entry<String, NavigatorConfig> entry : navigators.entrySet()) {
                    logger.info("Test Navigator '{}': {} slots configured", 
                        entry.getKey(), entry.getValue().slots().size());
                }
                
            } catch (Exception e) {
                logger.error("Failed to load test-config.yaml", e);
                throw new RuntimeException("Test config loading failed", e);
            }
        }
        
        @Override
        public NavigatorConfig getNavigatorConfig(String navigatorName) {
            if (appConfig == null || appConfig.navigators() == null) {
                logger.warn("Test config not loaded or navigators section is null");
                return new NavigatorConfig(List.of());
            }
            
            NavigatorConfig config = appConfig.navigators().get(navigatorName);
            if (config == null) {
                logger.warn("Navigator '{}' not found in test config", navigatorName);
                return new NavigatorConfig(List.of());
            }
            
            return config;
        }
    }
}
