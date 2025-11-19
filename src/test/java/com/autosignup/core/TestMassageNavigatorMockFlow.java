package com.autosignup.core;

import com.autosignup.model.Appointment;
import com.autosignup.model.config.*;
import com.autosignup.model.protocol.ProtocolFactory;
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

public class TestMassageNavigatorMockFlow {
    private static final Logger logger = LoggerFactory.getLogger(TestMassageNavigatorMockFlow.class);
    private static final String TEST_DB_PATH = "test-massage-mock-flow.db";
    
    private BotDBManager botDBManager;
    private PlaywrightWrapper playwright;
    private MockMassageNavigator massageNavigator;

    @Before
    public void setup() {
        logger.info("Spinning up massage mock flow test rig…");
        
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        botDBManager = new BotDBManager("jdbc:sqlite:" + TEST_DB_PATH);
        ProtocolFactory protocolFactory = new ProtocolFactory(botDBManager);
        
        playwright = new PlaywrightWrapper();

        MockConfigLoaderService configLoader = new MockConfigLoaderService();
        configLoader.loadConfig();
        List<String> testUrls = configLoader.getTestUrls();
        logger.info("Using test URLs: {}", String.join(", ", testUrls));
        
        massageNavigator = new MockMassageNavigator(protocolFactory, playwright, configLoader, botDBManager);
        massageNavigator.setUrls(testUrls);
        massageNavigator.loadConfig();
        forceEnableNavigator(massageNavigator);
        
        logger.info("Navigator ready: {} site(s), {} slot preference(s)",
                   massageNavigator.getWebsites().size(),
                   configLoader.getNavigatorConfig("massage_navigator").slots().size());
    }

    @After
    public void teardown() {
        logger.info("Cleaning up massage mock flow resources…");
        
        if (playwright != null) {
            playwright.close();
        }
        
        if (botDBManager != null) {
            botDBManager.close();
        }
        
        File dbFile = new File(TEST_DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        
        logger.info("Cleanup finished.");
    }

    @Test
    public void testMassageNavigatorMockFullFlow() {
        logger.info("Running massage mock flow…");
        
        List<Appointment> signedUpAppointments = massageNavigator.runAll();
        
        if (signedUpAppointments.isEmpty()) {
            logger.info("No mock signups happened likely no slots matched the preferred windows or the protocol vetoed them.");
        } else {
            logger.info("Pretending to grab {} appointment(s):", signedUpAppointments.size());
            signedUpAppointments.forEach(apt ->
                logger.info(" • {} from {} to {}", apt.summary(), apt.start(), apt.end())
            );
        }
        
        assertNotNull("runAll should return a list (not null)", signedUpAppointments);
    }


    private static class MockMassageNavigator extends VarsityMassageNavigator {
        public MockMassageNavigator(ProtocolFactory factory, PlaywrightWrapper playwright, ConfigLoaderService configLoader, BotDBManager botDBManager) {
            super(factory, playwright, configLoader, botDBManager);
        }
        
        @Override
        public boolean signup(Appointment appointment) {
            logger.info("Mocking signup for {} from {} to {}", appointment.summary(), appointment.start(), appointment.end());
            return true;
        }
    }

    private void forceEnableNavigator(VarsityMassageNavigator navigator) {
        try {
            var field = VarsityMassageNavigator.class.getDeclaredField("enabled");
            field.setAccessible(true);
            field.setBoolean(navigator, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to force-enable navigator for test", e);
        }
    }

    private static class MockConfigLoaderService extends ConfigLoaderService {
        private AppConfig appConfig;
        private List<String> testUrls;

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
                
                SignupUserConfig signupUser = null;
                @SuppressWarnings("unchecked")
                Map<String, Object> signupUserData = (Map<String, Object>) data.get("signup_user");
                
                if (signupUserData != null) {
                    String firstName = (String) signupUserData.get("first_name");
                    String lastName = (String) signupUserData.get("last_name");
                    String email = (String) signupUserData.get("email");
                    String phone = (String) signupUserData.get("phone");
                    
                    signupUser = new SignupUserConfig(firstName, lastName, email, phone);
                }
                
                appConfig = new AppConfig(checkInterval, navigators, signupUser);
                
                @SuppressWarnings("unchecked")
                Map<String, List<String>> testUrlsData = (Map<String, List<String>>) data.get("test_urls");
                if (testUrlsData != null) {
                    testUrls = testUrlsData.get("massage");
                    if (testUrls == null) {
                        testUrls = new ArrayList<>();
                    }
                } else {
                    testUrls = new ArrayList<>();
                }
                
                logger.info("Loaded mock config for {} navigator(s)", navigators.size());
                
            } catch (Exception e) {
                logger.error("Failed to load test-config.yaml", e);
                throw new RuntimeException("Mock config loading failed", e);
            }
        }
        
        @Override
        public NavigatorConfig getNavigatorConfig(String navigatorName) {
            if (appConfig == null || appConfig.navigators() == null) {
                logger.warn("Mock config not loaded or navigators section is null");
                return new NavigatorConfig(List.of());
            }
            
            NavigatorConfig config = appConfig.navigators().get(navigatorName);
            if (config == null) {
                logger.warn("Navigator '{}' not found in mock config", navigatorName);
                return new NavigatorConfig(List.of());
            }
            
            return config;
        }
        
        @Override
        public SignupUserConfig getSignupUserConfig() {
            if (appConfig == null) {
                logger.warn("Mock config not loaded");
                return null;
            }
            
            return appConfig.signupUser();
        }
        
        public List<String> getTestUrls() {
            return testUrls != null ? new ArrayList<>(testUrls) : new ArrayList<>();
        }
    }
}