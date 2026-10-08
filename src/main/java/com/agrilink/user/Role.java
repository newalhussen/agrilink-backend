package com.agrilink.user;

public enum Role {
    FARMER, BUYER, DRIVER, ADMIN;

    /** Roles a person may choose at self-registration. */
    public boolean isSelfRegistrable() {
        return this != ADMIN;
    }
}
