package com.autosignup.website;

import com.autosignup.model.Appointment;
import com.autosignup.model.WebsiteSpecs;
import com.autosignup.model.protocol.ProtocolFactory;
import com.autosignup.model.protocol.SignupProtocol;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

public abstract class WebsiteNavigator {
    protected final List<WebsiteSpecs> websites;
    protected final ProtocolFactory factory;
    protected SignupProtocol protocol = null;

    public WebsiteNavigator(ProtocolFactory factory) {
        this.factory = factory;
        this.websites = new ArrayList<>();
    }

    @Retryable(
            value = { IOException.class, TimeoutException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public abstract List<Appointment> navigate();

    @Recover
    public List<Appointment> recover(IOException ex, WebsiteSpecs specs) {
        System.out.println("Navigation failed for " + specs.url());
        return Collections.emptyList();
    }

    public List<Appointment> runAll() {
        return List.of();
    }
}

