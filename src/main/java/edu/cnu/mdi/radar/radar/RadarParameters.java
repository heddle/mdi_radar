package edu.cnu.mdi.radar.radar;

/**
 * An illustrative parameter bundle describing a notional radar's technical
 * display and coverage parameters.
 *
 * <h2>IMPORTANT — these numbers are illustrative only</h2>
 * <p>
 * Every figure here is drawn from open, unclassified secondary sources
 * (think-tank reports, defense journalism, encyclopaedias). Actual system
 * performance is classified, published estimates vary widely, and real battery
 * locations are not public. Treat this as a geometry demo, not an intelligence
 * product. The headline detection ranges are maximum instrumented ranges
 * against high targets in clear line of sight; terrain masking only bites at
 * low target altitudes, which is exactly what {@code targetAltitude} in the
 * line-of-sight model explores.
 * </p>
 *
 * <p>
 * These parameters are meant to be attached to a RadarItem. The RadarItem
 * supplies the site latitude/longitude and pointing azimuth. This class
 * supplies radar-type parameters such as antenna height, maximum display range,
 * azimuth field of regard, minimum elevation angle, and basing type.
 * </p>
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li>Azimuth width is the total angular width of the sector in degrees.</li>
 *   <li>Heights and ranges are in meters.</li>
 *   <li>A 360 degree azimuth width represents an all-around surveillance radar.</li>
 * </ul>
 *
 * @param name             display label / radar name for display purposes
 * @param shortName        short display label for compact displays
 * @param basing           radar basing type
 * @param antennaHeightAglM antenna phase-centre height above local ground (m)
 * @param maxRangeM        maximum illustrative/instrumented display range (m)
 * @param azimuthWidthDeg  total angular width of the sector (degrees)
 * @param minElevationDeg  lowest beam elevation angle (degrees)
 */
public record RadarParameters(
        String name,
        String shortName,
        RadarBasing basing,
        double antennaHeightAglM,
        double maxRangeM,
        double azimuthWidthDeg,
        double minElevationDeg) {	
   /**
     * Illustrative open-source preset for the THAAD AN/TPY-2 radar.
     * @return THAAD radar parameters
     */
    public static RadarParameters thaad() {
        return new RadarParameters(
                "THAAD AN/TPY-2",
                "THAAD",
                RadarBasing.GROUND,
                8.0,
                600_000.0,
                120.0,
                0.0);
    }
    
    /**
     * Illustrative open-source preset for the Patriot AN/MPQ-65 radar.
     * @return Patriot radar parameters
     */
    public static RadarParameters patriot() {
        return new RadarParameters(
                "Patriot AN/MPQ-65",
                "Patriot",
                RadarBasing.GROUND,
                6.0,
                160_000.0,
                120.0,
                0.0);
    }
    
    /**
     * Illustrative open-source preset for the S1850M naval long-range radar.
     *
     * <p>S1850M is a NATO naval long-range air-surveillance radar derived from
     * SMART-L. Public sources commonly describe it as a wide-area search radar
     * with a range of around 400 km.</p>
     *
     * @return S1850M radar parameters
     */
    public static RadarParameters s1850m() {
        return new RadarParameters(
                "S1850M Naval Long-Range Radar",
                "S1850M",
                RadarBasing.SHIP,
                25.0,        // illustrative shipboard antenna height AGL / above sea surface
                400_000.0,
                360.0,
                0.0);
    }
    
    /**
     * Illustrative open-source preset for the U.S. Navy AN/SPY-6 family.
     *
     * <p>Official public sources describe SPY-6 as a shipboard air and missile
     * defense radar family, but do not provide a simple public maximum range
     * suitable for this demo. The range used here is a conservative display
     * radius, not a claimed system performance figure.</p>
     *
     * @return AN/SPY-6 radar parameters
     */
    public static RadarParameters spy6() {
        return new RadarParameters(
                "AN/SPY-6 Naval Radar",
                "SPY-6",
                RadarBasing.SHIP,
                25.0,
                400_000.0,   // illustrative display radius, not an official spec
                360.0,
                0.0);
    }

    /**
	 * Returns a string array representation of the radar parameters for display purposes.
	 *
	 * @return an array of strings representing the radar parameters
	 */
    public String[] toStringArray() {
		return new String[] {
			name,
			"basing: " + basing.toString(),
			String.format("antenna height %.2f m", antennaHeightAglM),
			String.format("max range: %.1f km",maxRangeM / 1000.0),
			String.format("azimuth width %.1f°", azimuthWidthDeg),
			String.format("min elevation %.1f°", minElevationDeg),
		};
	}
    
}