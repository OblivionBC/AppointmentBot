package com.autosignup.model.config;

import java.util.Map;

public record AppConfig(
        int check_interval_seconds,
        Map<String, NavigatorConfig> navigators,
        SignupUserConfig signupUser
) {
}

