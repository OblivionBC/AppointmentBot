package com.autosignup.model;


import com.autosignup.model.protocol.SignupProtocol;

public record WebsiteSpecs(SignupProtocol protocol, String url, AppointmentType appointmentType) {
}