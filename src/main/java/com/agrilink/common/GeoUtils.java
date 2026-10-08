package com.agrilink.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtils() {
    }

    /** Great-circle distance in kilometres. */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Distance between two addresses, or {@code null} when either lacks coordinates. */
    public static BigDecimal distanceKm(Address from, Address to) {
        if (from == null || to == null || !from.hasCoordinates() || !to.hasCoordinates()) {
            return null;
        }
        double km = haversineKm(from.getLatitude(), from.getLongitude(), to.getLatitude(), to.getLongitude());
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }
}
