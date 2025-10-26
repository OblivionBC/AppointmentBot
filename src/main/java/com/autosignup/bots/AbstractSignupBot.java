package com.autosignup.bots;

import com.autosignup.core.BotConfigManager;
import com.autosignup.core.BotDBManager;
import com.autosignup.core.BotJobRunner;

public abstract class AbstractSignupBot {
    protected BotConfigManager botConfigManager;
    protected BotDBManager botDBManager;
    protected BotJobRunner botJobRunner;
    public AbstractSignupBot() {
        botConfigManager = new BotConfigManager();
        botDBManager = new BotDBManager();
        botJobRunner = new BotJobRunner();
    }
    abstract public void signup();
}
