package com.autosignup.service;

import com.autosignup.model.config.AppConfig;
import com.autosignup.model.config.NavigatorConfig;
import com.autosignup.model.config.SignupUserConfig;
import com.autosignup.model.config.SlotConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ConfigLoaderService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigLoaderService.class);
    private AppConfig appConfig;

    @PostConstruct
    public void loadConfig() {
        try {
            Yaml yaml = new Yaml();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config.yaml");
            
            if (inputStream == null) {
                throw new RuntimeException("config.yaml not found in resources");
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
                String studentNumber = (String) signupUserData.get("student_number");
                
                signupUser = new SignupUserConfig(firstName, lastName, email, phone, studentNumber);
                logger.info("Loaded signup user config: {}", signupUser);
            } else {
                logger.warn("No signup_user configuration found in config.yaml");
            }
            
            appConfig = new AppConfig(checkInterval, navigators, signupUser);
            
            logger.info("Successfully loaded config with {} navigators", navigators.size());
            for (Map.Entry<String, NavigatorConfig> entry : navigators.entrySet()) {
                logger.info("Navigator '{}': {} slots configured", 
                    entry.getKey(), entry.getValue().slots().size());
            }
            
        } catch (Exception e) {
            logger.error("Failed to load config.yaml", e);
            throw new RuntimeException("Config loading failed", e);
        }
    }
    
    public NavigatorConfig getNavigatorConfig(String navigatorName) {
        if (appConfig == null || appConfig.navigators() == null) {
            logger.warn("Config not loaded or navigators section is null");
            return new NavigatorConfig(List.of());
        }
        
        NavigatorConfig config = appConfig.navigators().get(navigatorName);
        if (config == null) {
            logger.warn("Navigator '{}' not found in config", navigatorName);
            return new NavigatorConfig(List.of());
        }
        
        return config;
    }
    
    public SignupUserConfig getSignupUserConfig() {
        if (appConfig == null) {
            logger.warn("Config not loaded");
            return null;
        }
        
        return appConfig.signupUser();
    }
}
