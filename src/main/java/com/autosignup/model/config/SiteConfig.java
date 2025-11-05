package com.autosignup.model.config;

import java.util.List;

public record SiteConfig(String name, String url, int priority, List<SlotConfig> slots) {
}

