package com.autosignup.model.protocol;

import com.autosignup.model.AppointmentType;
import com.autosignup.service.BotDBManager;
import org.springframework.stereotype.Component;

@Component
public class ProtocolFactory {
    private final BotDBManager dbManager;

    public ProtocolFactory(BotDBManager dbManager) {
        this.dbManager = dbManager;
    }

    public TypeWeeksBasedProtocol createTypeWeeksProtocol(int weeks) {
        return new TypeWeeksBasedProtocol(dbManager, weeks);
    }

    public TypeTimeBasedProtocol createTypeTimeProtocol(int hours) {
        return new TypeTimeBasedProtocol(dbManager, hours);
    }
}