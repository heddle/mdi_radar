package edu.cnu.mdi.radar.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.cnu.mdi.view.AbstractViewInfo;

/**
 * Provides metadata and help text for the {@link RadarView} information dialog.
 *
 * <p>
 * {@code RadarViewInfo} describes the radar/map demonstration view rather than
 * the generic base map view. It explains the purpose of the view, the main user
 * interactions, the radar placement rules, the target-altitude controls, and the
 * technical features being demonstrated.
 * </p>
 *
 * <h2>Role in the MDI view-info system</h2>
 * <p>
 * The MDI framework displays information dialogs using subclasses of
 * {@link AbstractViewInfo}. The subclass supplies structured plain-text content:
 * a title, purpose paragraph, usage bullets, keyboard shortcuts, technical notes,
 * and an optional footer. {@code RadarViewInfo} is returned by
 * {@link RadarView#getViewInfo()} so the Radar View does not inherit the more
 * general {@code MapViewInfo} text.
 * </p>
 *
 * <h2>Live view reference</h2>
 * <p>
 * A reference to the owning {@link RadarView} is retained so that technical
 * notes can report live map-data counts and the current target-altitude scale.
 * This avoids hardcoding values that may change as resources or controls evolve.
 * </p>
 */
public class RadarViewInfo extends AbstractViewInfo {

    /** The owning radar view. */
    private final RadarView radarView;

    /**
     * Creates a radar-view information object.
     *
     * @param radarView the owning radar view; must not be {@code null}
     * @throws IllegalArgumentException if {@code radarView} is {@code null}
     */
    public RadarViewInfo(RadarView radarView) {
        if (radarView == null) {
            throw new IllegalArgumentException("radarView cannot be null");
        }
        this.radarView = radarView;
    }

    /**
     * Returns the title shown at the top of the information dialog.
     *
     * @return dialog title
     */
    @Override
    public String getTitle() {
        return "Radar View";
    }

    /**
     * Returns a short description of the view's purpose.
     *
     * @return purpose text
     */
    @Override
    public String getPurpose() {
        return "Radar View is a domain-specific extension of the MDI base map "
             + "view. It combines map projections, country and city rendering, "
             + "ETOPO5 terrain and bathymetry, radar preset placement, and "
             + "terrain-aware line-of-sight visualization. The view is intended "
             + "as both a radar/map demonstration and an example of how a "
             + "specialized application can build on the MDI mapping framework.";
    }

    /**
     * Returns usage bullets displayed in the information dialog.
     *
     * @return usage bullet list
     */
    @Override
    public List<String> getUsageBullets() {
        return List.of(
            "Use the projection selector to switch among supported map projections.",
            "Use the map controls to show or hide city names, graticules, graticule labels, ETOPO5 terrain, and hover feedback.",
            "Use the view popup menu to jump quickly to the Global, Korean Theater, or USCENTCOM AOR views.",
            "Select a radar preset in the Radar Presets palette, then click the map to place it.",
            "Ground-based radar presets must be placed on land; ship-based radar presets must be placed on water.",
            "Use the pointer tool to select, drag, or rotate radar items. Circular 360-degree radars are not rotatable because azimuth has no visible meaning for a full circle.",
            "Use the Target Altitudes slider to scale the colored line-of-sight altitude thresholds. The legend below the slider shows the currently scaled values.",
            "Hover over a radar item to see its preset parameters, site latitude/longitude, and boresight azimuth in the feedback panel."
        );
    }

    /**
     * Returns keyboard and mouse shortcuts relevant to the radar view.
     *
     * @return ordered shortcut map
     */
    @Override
    public Map<String, String> getKeyboardShortcuts() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("Scroll Wheel", "Zoom in and out.");
        keys.put("Right Click + Drag", "Pan the map.");
        keys.put("Right Click", "Open the view popup menu, including quick zoom locations.");
        keys.put("Pointer Tool", "Select, drag, and rotate radar items.");
        keys.put("Radar Preset Button", "Activate radar placement mode; then click the map to place the selected preset.");
        return keys;
    }

    /**
     * Returns technical notes describing the map and radar implementation.
     *
     * <p>
     * The country and city counts are obtained from the live view, as is the
     * current target-altitude scale.
     * </p>
     *
     * @return technical notes
     */
    @Override
    public String getTechnicalNotes() {
        return String.format(
            "Radar View extends MapView2D and uses MapContainer for projection-aware "
          + "drawing and interaction. Countries loaded: %d. Cities loaded: %d. "
          + "ETOPO5 terrain and bathymetry are loaded from the MDI classpath resources "
          + "and are used both for shaded terrain display and radar line-of-sight "
          + "calculations. Radar preset buttons are external toolbar toggles: they are "
          + "displayed in the side palette but participate in the toolbar's mutually "
          + "exclusive active-tool group. RadarItem stores its site in geographic "
          + "coordinates and rebuilds its projected fan on each draw so projection seams "
          + "are handled correctly. The current target-altitude scale is %.1fx.",
            radarView.getCountryCount(),
            radarView.getCityCount(),
            radarView.getTargetAltitudeScale()
        );
    }

    /**
     * Returns footer text for the information dialog.
     *
     * @return footer text
     */
    @Override
    public String getFooter() {
        return "Radar View: MDI map extension, terrain display, and radar line-of-sight demonstration";
    }

    /**
     * Returns the accent color used for section headers.
     *
     * @return accent color as a hex string
     */
    @Override
    protected String getAccentColorHex() {
        return "#1f78b4";
    }
}