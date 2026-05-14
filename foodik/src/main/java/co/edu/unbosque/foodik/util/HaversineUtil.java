package co.edu.unbosque.foodik.util;
import org.springframework.stereotype.Component;

@Component
public class HaversineUtil {
    private static final double EARTH_RADIUS_KM = 6371.0;

    public double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public double[] boundingBox(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / EARTH_RADIUS_KM * (180 / Math.PI);
        double lngDelta = radiusKm / (EARTH_RADIUS_KM * Math.cos(Math.toRadians(lat))) * (180 / Math.PI);
        return new double[]{lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta};
    }
}
