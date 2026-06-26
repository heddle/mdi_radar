package edu.cnu.mdi.radar.geo;

import java.awt.geom.Point2D;

/**
 * Minimal spherical-Earth geodesy helpers used by the terrain / radar demo.
 *
 * <h2>Coordinate convention</h2>
 * <p>
 * Geographic points follow the MDI mapping convention: a {@link Point2D.Double}
 * where {@code x = longitude} and {@code y = latitude}, both in <strong>radians</strong>.
 * Longitude is normalized to {@code (-pi, pi]}.
 * </p>
 *
 * <p>
 * A sphere is more than accurate enough for a terrain-masking visualization:
 * the forward-geodesic error over a few hundred kilometres is far below the
 * resolution of the elevation grid that drives the masking. If you later want
 * survey-grade accuracy, swap the body of {@link #destination} for a Vincenty
 * or Karney forward solution; nothing else in the project needs to change.
 * </p>
 *
 * <p>This class is not instantiable.</p>
 */
public final class Geodesy {

    /** Mean Earth radius in metres (IUGG). */
    public static final double EARTH_RADIUS_M = 6_371_008.8;

    /**
     * Effective-Earth-radius factor for standard atmospheric refraction.
     *
     * <p>The classic "four-thirds Earth" approximation: radio waves bend
     * slightly toward the surface, so the radar horizon sits a bit farther out
     * than the geometric horizon. Multiply {@link #EARTH_RADIUS_M} by this to
     * get the effective radius used in elevation-angle calculations.</p>
     */
    public static final double REFRACTION_K = 4.0 / 3.0;

    private Geodesy() {
        // not instantiable
    }

    /**
     * Normalizes a longitude (radians) into the half-open range {@code (-pi, pi]}.
     *
     * @param lonRad longitude in radians
     * @return equivalent longitude in {@code (-pi, pi]}
     */
    public static double normalizeLon(double lonRad) {
        double x = (lonRad + Math.PI) % (2.0 * Math.PI);
        if (x <= 0.0) {
            x += 2.0 * Math.PI;
        }
        return x - Math.PI;
    }

    /**
     * Forward geodesic on a sphere: given a start point, an initial bearing,
     * and a great-circle distance, returns the destination point.
     *
     * @param latRad     start latitude in radians
     * @param lonRad     start longitude in radians
     * @param bearingRad initial bearing in radians, measured clockwise from
     *                   true north
     * @param distanceM  great-circle distance in metres
     * @param out        reused output point ({@code x = lon, y = lat}, radians);
     *                   must not be {@code null}
     * @return {@code out}, for call chaining
     */
    public static Point2D.Double destination(double latRad, double lonRad,
                                             double bearingRad, double distanceM,
                                             Point2D.Double out) {
        double angular = distanceM / EARTH_RADIUS_M; // central angle (radians)

        double sinLat1 = Math.sin(latRad);
        double cosLat1 = Math.cos(latRad);
        double sinAng = Math.sin(angular);
        double cosAng = Math.cos(angular);

        double sinLat2 = sinLat1 * cosAng + cosLat1 * sinAng * Math.cos(bearingRad);
        double lat2 = Math.asin(Math.max(-1.0, Math.min(1.0, sinLat2)));

        double y = Math.sin(bearingRad) * sinAng * cosLat1;
        double x = cosAng - sinLat1 * sinLat2;
        double lon2 = lonRad + Math.atan2(y, x);

        out.x = normalizeLon(lon2);
        out.y = lat2;
        return out;
    }

    /**
     * Apparent elevation angle (radians) from an antenna to a point at the
     * given ground range and mean-sea-level height, including the four-thirds
     * Earth curvature drop for standard refraction.
     *
     * <p>Small-angle form: {@code el = (h - hAnt)/s - s / (2 k R)}. The first
     * term is the straight-line slope; the second subtracts the curvature drop,
     * which is what makes a distant sea-level target fall below the radar
     * horizon even over perfectly flat water.</p>
     *
     * @param antennaHeightM antenna phase-centre height (metres MSL)
     * @param targetHeightM  target height (metres MSL)
     * @param groundRangeM   ground range from antenna to target (metres, &gt; 0)
     * @return apparent elevation angle in radians (positive = above horizontal)
     */
    public static double elevationAngle(double antennaHeightM, double targetHeightM,
                                        double groundRangeM) {
        if (groundRangeM <= 0.0) {
            return Math.PI / 2.0;
        }
        double effectiveR = REFRACTION_K * EARTH_RADIUS_M;
        return (targetHeightM - antennaHeightM) / groundRangeM
                - groundRangeM / (2.0 * effectiveR);
    }
    
    /**
     * Required target height for a given apparent elevation angle, range, and
     * antenna height, including the four-thirds-Earth curvature correction.
     *
     * <p>This is the inverse of {@link #elevationAngle(double, double, double)}:
     * {@code h = hAnt + s * el + s^2 / (2 k R)}.</p>
     *
     * @param antennaHeightM antenna phase-centre height, metres MSL
     * @param groundRangeM ground range from antenna to target, metres
     * @param elevationAngleRad apparent elevation angle, radians
     * @return required target height, metres MSL
     */
    public static double targetHeightForElevationAngle(double antennaHeightM,
                                                       double groundRangeM,
                                                       double elevationAngleRad) {
        if (groundRangeM <= 0.0) {
            return antennaHeightM;
        }

        double effectiveR = REFRACTION_K * EARTH_RADIUS_M;
        return antennaHeightM
                + groundRangeM * elevationAngleRad
                + (groundRangeM * groundRangeM) / (2.0 * effectiveR);
    }
}