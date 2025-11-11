package com.autosignup.util;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class PlaywrightWrapper {
    private static final Logger logger = LoggerFactory.getLogger(PlaywrightWrapper.class);
    
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    @Getter
    private Page page;

    public PlaywrightWrapper() {
        initializeBrowser();
    }
    
    private void initializeBrowser() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setSlowMo(1000));
            
            context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));
            
            page = context.newPage();
            page.setDefaultTimeout(30000);
            
            logger.info("Browser initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize browser", e);
            throw new RuntimeException("Browser initialization failed", e);
        }
    }

    public void navigateTo(String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Navigating to {} (attempt {})", url, attempt);
                page.navigate(url);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                return;
            } catch (Exception e) {
                logger.warn("Navigation attempt {} failed: {}", attempt, e.getMessage());
                
                if (e.getMessage().contains("collected") || e.getMessage().contains("heap growth")) {
                    logger.warn("Detected memory issue, recreating browser context");
                    try {
                        recreateBrowserContext();
                    } catch (Exception recreateError) {
                        logger.error("Failed to recreate context: {}", recreateError.getMessage());
                    }
                }
                
                if (attempt == maxRetries) {
                    throw new RuntimeException("Failed to navigate after " + maxRetries + " attempts", e);
                }
                sleepWithBackoff(attempt);
            }
        }
    }

    public List<ElementHandle> findSlots(Map<String, String> selectors) {
        try {
            logger.info("Looking for appointment slots");
            
            if (!selectors.containsKey("slot_container")) {
                throw new IllegalArgumentException("slot_container selector is required");
            }
            
            try {
                logger.debug("Looking for 'Hide Full Spots' checkbox");
                Object isChecked = page.evaluate("() => { const checkbox = document.querySelector('input[ng-model=\"$ctrl.hideFullSpotsLocal\"]'); if (checkbox && !checkbox.checked) { checkbox.click(); return true; } return false; }");
                if (Boolean.TRUE.equals(isChecked)) {
                    logger.info("Clicked 'Hide Full Spots' checkbox to show only available slots");
                    page.waitForTimeout(2000);
                } else {
                    logger.debug("'Hide Full Spots' checkbox already checked or not found");
                }
            } catch (Exception e) {
                logger.debug("Could not click 'Hide Full Spots' checkbox: {}", e.getMessage());
            }
            
            page.waitForSelector(selectors.get("slot_container"), new Page.WaitForSelectorOptions().setTimeout(15000));
            List<ElementHandle> slots = page.querySelectorAll(selectors.get("slot_container"));
            logger.info("Found {} potential slots", slots.size());
            
            return slots;
            
        } catch (Exception e) {
            logger.error("Failed to find slots: {}", e.getMessage());
            
            logger.warn("Initial slot search failed, attempting to click 'Hide Full Spots' and retry");
            try {
                page.evaluate("() => { const checkbox = document.querySelector('input[ng-model=\"$ctrl.hideFullSpotsLocal\"]'); if (checkbox && !checkbox.checked) { checkbox.click(); } }");
                logger.info("Clicked 'Hide Full Spots' checkbox on retry");
                page.waitForTimeout(3000);
                
                List<ElementHandle> slots = page.querySelectorAll(selectors.get("slot_container"));
                if (!slots.isEmpty()) {
                    logger.info("Retry successful! Found {} potential slots after expanding dropdowns", slots.size());
                    return slots;
                } else {
                    logger.warn("No slots found even after expanding dropdowns");
                }
            } catch (Exception retryEx) {
                logger.error("Retry also failed: {}", retryEx.getMessage());
            }
            
            takeScreenshot("find_slots_failed");
            return List.of();
        }
    }

    public String getElementText(ElementHandle element) {
        try {
            return element.textContent();
        } catch (Exception e) {
            logger.error("Failed to get element text: {}", e.getMessage());
            return "";
        }
    }

    public void takeScreenshot(String name) {
        try {
            String filename = String.format("screenshot_%s_%d.png", name, System.currentTimeMillis());
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(filename)));
            logger.info("Screenshot saved: {}", filename);
        } catch (Exception e) {
            logger.error("Failed to take screenshot: {}", e.getMessage());
        }
    }

    private void sleepWithBackoff(int attempt) {
        try {
            long delay = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
            Thread.sleep(Math.min(delay, 10000)); // Max 10 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void recreateBrowserContext() {
        try {
            logger.info("Recreating browser context to prevent memory leaks");
            
            if (page != null) {
                page.close();
            }
            if (context != null) {
                context.close();
            }
            
            context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"));
            
            page = context.newPage();
            page.setDefaultTimeout(30000);
            
            logger.info("Browser context recreated successfully");
        } catch (Exception e) {
            logger.error("Failed to recreate browser context, reinitializing entire browser", e);
            close();
            initializeBrowser();
        }
    }


    public void clickElement(ElementHandle element) {
        try {
            logger.debug("Clicking element");
            element.click();
            page.waitForTimeout(1000); // Wait for any animations
            logger.debug("Element clicked successfully");
        } catch (Exception e) {
            logger.error("Failed to click element: {}", e.getMessage());
            throw new RuntimeException("Element click failed", e);
        }
    }

    public void fillFormField(String selector, String value) {
        try {
            logger.debug("Filling form field: {} with value: {}", selector, value);
            page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(10000));
            page.fill(selector, value);
            logger.debug("Form field filled successfully");
        } catch (Exception e) {
            logger.error("Failed to fill form field {}: {}", selector, e.getMessage());
            throw new RuntimeException("Form field fill failed: " + selector, e);
        }
    }

    public boolean waitForModal(String selector, int timeoutMs) {
        try {
            logger.debug("Waiting for modal/element: {}", selector);
            page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(timeoutMs));
            logger.debug("Modal/element appeared: {}", selector);
            return true;
        } catch (Exception e) {
            logger.warn("Modal/element did not appear within {}ms: {}", timeoutMs, selector);
            return false;
        }
    }

    public boolean isElementVisible(String selector) {
        try {
            logger.debug("Checking visibility of element: {}", selector);
            ElementHandle element = page.querySelector(selector);
            if (element == null) {
                logger.debug("Element not found: {}", selector);
                return false;
            }
            boolean visible = element.isVisible();
            logger.debug("Element {} is visible: {}", selector, visible);
            return visible;
        } catch (Exception e) {
            logger.debug("Error checking element visibility: {}", e.getMessage());
            return false;
        }
    }

    public void clickSelector(String selector) {
        try {
            logger.debug("Clicking selector: {}", selector);
            page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(10000));
            page.click(selector);
            page.waitForTimeout(1000); // Wait for any animations
            logger.debug("Selector clicked successfully: {}", selector);
        } catch (Exception e) {
            logger.error("Failed to click selector {}: {}", selector, e.getMessage());
            throw new RuntimeException("Selector click failed: " + selector, e);
        }
    }

    public void close() {
        try {
            if (page != null) {
                page.close();
            }
            if (context != null) {
                context.close();
            }
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
            logger.info("Browser closed successfully");
        } catch (Exception e) {
            logger.error("Error closing browser", e);
        }
    }

}
