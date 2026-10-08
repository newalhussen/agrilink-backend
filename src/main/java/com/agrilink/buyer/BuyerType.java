package com.agrilink.buyer;

public enum BuyerType {
    INDIVIDUAL, RESTAURANT, HOTEL, SUPERMARKET, GROCER, WHOLESALER, PROCESSOR, INSTITUTION, EXPORTER, OTHER;

    /** Business buyers are asked for a trade licence during verification. */
    public boolean isBusiness() {
        return this != INDIVIDUAL;
    }
}
