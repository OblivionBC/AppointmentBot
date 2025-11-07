package com.autosignup.model.protocol;

import com.autosignup.model.SlotInfo;
import com.autosignup.service.BotDBManager;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class SignupProtocol {
    protected final BotDBManager dbManager;

    // Returns true if this slot should be signed up for
    public abstract boolean checkValidity(SlotInfo slot);
}