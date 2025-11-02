package com.autosignup.model;

public enum AppointmentType {
    MASSAGE("massage"), PHYSIO("physio"), MAINTENANCE("maintenance");
    private String value;
    AppointmentType(String value) {
        this.value = value;
    }
}
