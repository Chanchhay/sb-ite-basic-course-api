package kh.edu.istad.ite.shared.helper;

public final class GeoDistanceHelper {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceHelper() {
    }

    /**
     * Straight-line ("as the crow flies") distance in kilometers, via the
     * Haversine formula — not a driving distance. Good enough to rank stores
     * near-first; revisit with a routing API only if shoppers ask for an ETA.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
