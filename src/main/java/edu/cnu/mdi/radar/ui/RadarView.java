package edu.cnu.mdi.radar.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.Border;

import edu.cnu.mdi.component.CommonBorder;
import edu.cnu.mdi.graphics.toolbar.BaseToolBar;
import edu.cnu.mdi.graphics.toolbar.ToolBits;
import edu.cnu.mdi.hover.HoverEvent;
import edu.cnu.mdi.hover.HoverInfoWindow;
import edu.cnu.mdi.item.AItem;
import edu.cnu.mdi.item.Layer;
import edu.cnu.mdi.mapping.MapResources;
import edu.cnu.mdi.mapping.MapView2D;
import edu.cnu.mdi.mapping.container.MapContainer;
import edu.cnu.mdi.mapping.loader.Etopo5Loader;
import edu.cnu.mdi.mapping.loader.GeoJsonCityLoader;
import edu.cnu.mdi.mapping.loader.GeoJsonCountryLoader;
import edu.cnu.mdi.mapping.loader.GeoJsonCountryLoader.CountryFeature;
import edu.cnu.mdi.mapping.milsym.NatoIconPicker;
import edu.cnu.mdi.radar.item.RadarItem;
import edu.cnu.mdi.radar.radar.RadarBasing;
import edu.cnu.mdi.radar.radar.RadarParameters;
import edu.cnu.mdi.ui.fonts.Fonts;
import edu.cnu.mdi.ui.menu.ViewPopupMenu;
import edu.cnu.mdi.util.Environment;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.AbstractViewInfo;
import edu.cnu.mdi.view.ContainerFactory;

/**
 * A radar-oriented map view that combines MDI map rendering, ETOPO5 terrain
 * shading, NATO military-symbol placement, radar preset placement, and radar
 * line-of-sight visualization.
 *
 * <p>
 * The view demonstrates several MDI extension mechanisms:
 * </p>
 * <ul>
 *   <li>standard map rendering from {@link MapView2D},</li>
 *   <li>a west-side palette containing NATO symbols and radar preset buttons,</li>
 *   <li>external toggle buttons registered with the toolbar,</li>
 *   <li>a custom radar item layer,</li>
 *   <li>view-popup quick zoom locations,</li>
 *   <li>ETOPO5 elevation rendering,</li>
 *   <li>and a target-altitude scale control used by radar LOS drawing.</li>
 * </ul>
 *
 * <p>
 * Radar placement is constrained by basing type. Ground-based radar presets must
 * be placed on land, while ship-based radar presets must be placed on water.
 * </p>
 */
@SuppressWarnings("serial")
public class RadarView extends MapView2D {

    /** Whether hovering over radar items should show a tooltip. */
    private boolean enableHovering = true;


    /**
     * Scale factor applied to the target-altitude thresholds used by
     * {@link RadarItem} colored LOS rays.
     */
    private double targetAltitudeScale = 1.0;

    /** The layer on which radar items are created. */
    private Layer radarLayer;

    /**
     * Creates the radar view.
     *
     * <p>
     * The constructor initializes the base map view, loads ETOPO5 terrain,
     * loads country and city GeoJSON resources, creates the radar-item layer,
     * installs the west-side palettes, adds the ETOPO5 display checkbox, and
     * extends the view popup with quick zoom entries.
     * </p>
     */
    public RadarView() {
        super(defaults());
        setBackground(Color.BLACK);

 
        try {
            String resPrefix = Environment.MDI_RESOURCE_PATH;

            installEtopo5Layer(
                    Etopo5Loader.loadDefaultResource(),
                    false);

            String resStr =
                    (resPrefix + MapResources.COUNTRIES_GEOJSON)
                            .replaceAll("//", "/");

            List<CountryFeature> countries =
                    GeoJsonCountryLoader
                            .loadFromResourceStatic(resStr);

            setCountries(countries);

            resStr =
                    (resPrefix + MapResources.CITIES_GEOJSON)
                            .replaceAll("//", "/");

            setCities(
                    GeoJsonCityLoader
                            .loadFromResourceStatic(resStr));

            addWestPanel(createWestPalettePanel());

        } catch (IOException e) {
            e.printStackTrace();
        }

        quickZoomMenu();

        // no filtering of city labels
        getCityRenderer().setMaxLabelScalerank(-1);
        
        // Create a new layer for radar items and add it to the container.
        radarLayer = new Layer(getIContainer(), "Radar Layer");

    }

    /**
     * Adds map-specific quick zoom entries to the view popup menu.
     *
     * <p>
     * These entries are intentionally view-level shortcuts rather than toolbar
     * buttons. They provide convenient test cases for radar placement and terrain
     * visualization.
     * </p>
     */
    private void quickZoomMenu() {
        ViewPopupMenu menu = getViewPopupMenu();
        menu.addSeparator();

        JMenuItem wholeWorldZoom = new JMenuItem("Global");
        wholeWorldZoom.addActionListener(e -> {
            MapContainer container = (MapContainer) getIContainer();
            container.restoreDefaultWorld();
        });

        JMenuItem koreaZoom = new JMenuItem("Korean Theater");
        koreaZoom.addActionListener(e -> {
            MapContainer container = (MapContainer) getIContainer();
            container.zoomLatLon(33.54, 43.48, 123.29, 136.17);
        });

        JMenuItem iranTheaterZoom = new JMenuItem("USCENTCOM AOR");
        iranTheaterZoom.addActionListener(e -> {
            MapContainer container = (MapContainer) getIContainer();
            container.zoomLatLon(16.16, 43.0, 37.76, 60.57);
        });

        menu.add(wholeWorldZoom);
        menu.add(koreaZoom);
        menu.add(iranTheaterZoom);
    }

    /**
     * Creates the west-side palette panel.
     *
     * <p>
     * The panel contains:
     * </p>
     * <ol>
     *   <li>the NATO military-symbol picker,</li>
     *   <li>the radar preset palette,</li>
     *   <li>and the target-altitude scale/legend panel.</li>
     * </ol>
     *
     * @return west-side palette panel
     */
    private JPanel createWestPalettePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        NatoIconPicker picker = new NatoIconPicker();
        picker.setAlignmentX(Component.LEFT_ALIGNMENT);

        RadarButtonPalette radarPalette = createRadarButtonPalette();

        Border innerBorder = BorderFactory.createEmptyBorder(2, 2, 2, 2);
        Border outerBorder = new CommonBorder("Radar Presets");
        radarPalette.setBorder(BorderFactory.createCompoundBorder(outerBorder, innerBorder));

        radarPalette.setAlignmentX(Component.LEFT_ALIGNMENT);
        radarPalette.packPaletteSize();

        panel.add(picker);
        panel.add(Box.createVerticalStrut(8));
        panel.add(radarPalette);
        panel.add(Box.createVerticalStrut(6));
        panel.add(createTargetAltitudePanel());

        return panel;
    }

    /**
     * Creates the target-altitude scale panel.
     *
     * <p>
     * The slider controls the scale factor applied to the base target-altitude
     * thresholds used by {@link RadarItem}. The legend below the slider shows the
     * currently scaled thresholds and their corresponding LOS ray colors.
     * </p>
     *
     * @return target-altitude control panel
     */
    private JPanel createTargetAltitudePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        Border innerBorder = BorderFactory.createEmptyBorder(2, 4, 2, 4);
        Border outerBorder = new CommonBorder("Target Altitudes");
        panel.setBorder(BorderFactory.createCompoundBorder(outerBorder, innerBorder));

        JLabel label = new JLabel("Scale: 1.0x");
        label.setFont(Fonts.tweenFont);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSlider slider = new JSlider(1, 50, 10); // 0.1x to 5.0x
        slider.setOpaque(false);
        slider.setMajorTickSpacing(10);
        slider.setMinorTickSpacing(1);
        slider.setPaintTicks(true);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);

        TargetAltitudeLegend legend = new TargetAltitudeLegend();
        legend.setScale(targetAltitudeScale);
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);

        slider.addChangeListener(e -> {
            double scale = slider.getValue() / 10.0;
            label.setText(String.format("Scale: %.1fx", scale));
            legend.setScale(scale);

            /*
             * The radar drawing is fast enough to update live while the slider is
             * moving, so refresh on both adjusting and final events.
             */
            targetAltitudeScale = clamp(scale, 0.1, 5.0);
            refresh();
        });

        panel.add(label);
        panel.add(slider);
        panel.add(Box.createVerticalStrut(3));
        panel.add(legend);

        Dimension pref = panel.getPreferredSize();
        panel.setMaximumSize(pref);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        return panel;
    }

    /**
     * Creates the radar-button palette using all built-in radar presets.
     *
     * <p>
     * The buttons are visually placed in a palette, but each button is registered
     * as an external toolbar toggle so it participates in the same active-tool
     * selection group as pointer, pan, zoom, and other toolbar tools.
     * </p>
     *
     * @return radar button palette
     */
    private RadarButtonPalette createRadarButtonPalette() {
        BaseToolBar tb = (BaseToolBar) getToolBar();

        RadarButton thaad = new RadarButton(this, tb, RadarParameters.thaad());
        tb.registerExternalToggle("radar.thaad", thaad);

        RadarButton patriot = new RadarButton(this, tb, RadarParameters.patriot());
        tb.registerExternalToggle("radar.patriot", patriot);

        RadarButton s1850m = new RadarButton(this, tb, RadarParameters.s1850m());
        tb.registerExternalToggle("radar.s1850m", s1850m);

        RadarButton spy6 = new RadarButton(this, tb, RadarParameters.spy6());
        tb.registerExternalToggle("radar.spy6", spy6);

        List<RadarButton> buttons = List.of(thaad, patriot, s1850m, spy6);

        return new RadarButtonPalette(4, buttons);
    }

    /**
     * Tests whether a radar with the given parameters may be placed at a screen point.
     *
     * <p>
     * Ground-based radars must be placed on land. Ship-based radars must be placed
     * on water. The screen point is interpreted in this view's map container.
     * </p>
     *
     * @param parameters radar parameters
     * @param pp screen point to test
     * @param showWarning if {@code true}, show a warning dialog on invalid placement
     * @return {@code true} if the placement is allowed
     */
    public boolean isRadarPlacementAllowed(RadarParameters parameters,
                                           Point pp,
                                           boolean showWarning) {
        if (parameters == null || pp == null) {
            return false;
        }

        boolean onLand = this.onLand(pp, getIContainer());

        boolean ok = !((parameters.basing() == RadarBasing.GROUND && !onLand)
                || (parameters.basing() == RadarBasing.SHIP && onLand));

        if (!ok && showWarning) {
            warning(parameters);
        }

        return ok;
    }

    /**
     * Attempts to place a radar on the map at the specified screen point.
     *
     * <p>
     * Placement is checked against the radar basing type. Ground-based radars
     * must be placed on land; ship-based radars must be placed on water.
     * </p>
     *
     * @param parameters radar parameters to use for placement
     * @param pp         screen point where the radar is to be placed
     * @return {@code true} if the radar was placed; {@code false} otherwise
     */
    public boolean tryPlaceRadar(RadarParameters parameters, Point pp) {

        if (parameters == null || pp == null || !isRadarPlacementAllowed(parameters, pp, true)) {
            return false;
        }

        Point2D.Double latLon = new Point2D.Double();
        localToLatLonDeg(pp, latLon);

        new RadarItem(radarLayer, parameters, latLon.y, latLon.x);
        refresh();
        return true;
    }
    /**
     * Displays a warning message for invalid radar placement.
     *
     * @param parameters radar parameters whose basing rule was violated
     */
    private void warning(RadarParameters parameters) {
        String msg;
        switch (parameters.basing()) {
        case GROUND:
            msg = "Ground-based radar must be placed on land.";
            break;
        case SHIP:
            msg = "Ship-based radar must be placed on water.";
            break;
        default:
            return;
        }

        JOptionPane.showMessageDialog(
                this,
                msg,
                "Radar Placement Warning",
                JOptionPane.WARNING_MESSAGE);
    }

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * When the user hovers over the map, this method displays a small window with the
	 * name and ISO code of the country under the cursor, if any. The tooltip is
	 * shown in the {@link HoverInfoWindow} provided by the {@link MapContainer}.
	 * </p>
	 */
	@Override
	public void hoverUpdate(HoverEvent he) {

		// If hovering is disabled, do nothing.
		if (!enableHovering) {
			return;
		}

		Point pp = he.getLocation();
		HoverInfoWindow win = container.getHoverWindow();

		if ((win == null) || (pp == null)) {
			return;
		}

		List<RadarItem> radars = getAllRadars();
		if (radars == null || radars.isEmpty()) {
			return;
		}

		// Check if the hover point is over any radar item.
		for (RadarItem radar : radars) {
			if (radar.contains(container, pp)) {
				RadarParameters params = radar.getParameters();
				String[] strArray = params.toStringArray();

				StringBuilder sb = new StringBuilder();
				for (String str : strArray) {
					sb.append(str).append("\n");
				}

				sb.append(String.format(
                        "site Lat/Lon %.4f, %.4f\n",
                        radar.getSiteLatitudeDeg(),
                        radar.getSiteLongitudeDeg()));

				sb.append(String.format("boresight azimuth %.1f°", radar.getAzimuth()));

				win.showMessage(he, sb.toString());
				return;
			}
		}
	}



    /**
     * Get all the radar items.
     * Assumes all radars are on the radarLayer.
     * @return list of radar items
     */
    public List<RadarItem> getAllRadars() {
    	ArrayList<RadarItem> radars = new ArrayList<>();
    	for (AItem item : radarLayer.getAllItems()) {
    		if (item instanceof RadarItem) {
    			radars.add((RadarItem)item);
    		}
    	}
    	return radars;
    }



    @Override
    public AbstractViewInfo getViewInfo() {
        return new RadarViewInfo(this);
    }

    /**
     * Returns the current scale factor applied to radar target-altitude
     * thresholds.
     *
     * @return target-altitude scale
     */
    public double getTargetAltitudeScale() {
        return targetAltitudeScale;
    }

    /**
     * Clamps a value to a closed interval.
     *
     * @param value value to clamp
     * @param min   minimum value
     * @param max   maximum value
     * @return clamped value
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Returns the default property list used to construct this view.
     *
     * @return default key/value properties
     */
    private static Object[] defaults() {
        ContainerFactory mapContainerFactory = MapContainer::new;

        return new Object[] {
                PropertyUtils.TITLE, "Radar View",
                PropertyUtils.FRACTION, 0.8,
                PropertyUtils.ASPECT, 1.2,
                PropertyUtils.CONTAINERFACTORY, mapContainerFactory,
                PropertyUtils.TOOLBARBITS, ToolBits.MAPTOOLS | ToolBits.ZOOMTOOLS,
                PropertyUtils.WHEELZOOM, true
        };
    }

    /**
     * Compact legend showing the currently scaled target-altitude thresholds used
     * by radar LOS ray colors.
     *
     * <p>
     * The legend reads the base thresholds and colors from {@link RadarItem}, so
     * it stays synchronized with the actual rendering code. The final swatch shows
     * the masked color used when a point requires a target altitude above the
     * highest configured threshold.
     * </p>
     */
    private static class TargetAltitudeLegend extends JComponent {

        /** Width of each color swatch, in pixels. */
        private static final int SWATCH_W = 16;

        /** Height of each color swatch, in pixels. */
        private static final int SWATCH_H = 8;

        /** Horizontal gap between legend entries. */
        private static final int GAP = 3;

        /** Vertical gap between swatches and labels. */
        private static final int ROW_GAP = 3;

        /** Scale factor applied to base altitude thresholds. */
        private double scale = 1.0;

        /**
         * Sets the altitude scale used by the legend.
         *
         * @param scale scale factor applied to base thresholds
         */
        public void setScale(double scale) {
            this.scale = Math.max(0.1, scale);
            repaint();
        }

        /**
         * Returns the preferred size for the compact legend.
         *
         * @return preferred legend size
         */
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(205, 34);
        }

        /**
         * Paints the target-altitude legend.
         *
         * @param g graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            double[] base = RadarItem.getBaseTargetAltitudesM();
            Color[] colors = RadarItem.getTargetAltitudeColors();

            g.setFont(Fonts.tinyFont);
            FontMetrics fm = g.getFontMetrics();

            int x = 4;
            int y = 3;

            int count = Math.min(base.length, colors.length);

          //get the biggest width needed
            int maxWid = SWATCH_W;
            for (int i = 0; i < count; i++) {
                String label = altitudeLabel(base[i] * scale);
                maxWid = Math.max(maxWid, fm.stringWidth(label));
            }
            maxWid += GAP;


            for (int i = 0; i < count; i++) {
                g.setColor(colors[i]);
                g.fillRect(x, y, SWATCH_W, SWATCH_H);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, SWATCH_W, SWATCH_H);

                String label = altitudeLabel(base[i] * scale);
                int labelY = y + SWATCH_H + ROW_GAP + fm.getAscent();

                g.setColor(Color.BLACK);
                g.drawString(label, x, labelY);

                x += maxWid;
            }

            if (base.length > 0) {
                g.setColor(RadarItem.getMaskedColor());
                g.fillRect(x, y, SWATCH_W, SWATCH_H);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, SWATCH_W, SWATCH_H);

                double highest = base[base.length - 1] * scale;
                g.setColor(Color.BLACK);
                g.drawString(">" + altitudeLabel(highest),
                        x,
                        y + SWATCH_H + ROW_GAP + fm.getAscent());
            }
        }

        /**
         * Formats an altitude label compactly.
         *
         * @param meters altitude in metres
         * @return formatted label
         */
        private static String altitudeLabel(double meters) {
            if (meters >= 1000.0) {
                double km = meters / 1000.0;
                if (Math.abs(km - Math.rint(km)) < 1.0e-6) {
                    return String.format("%.0fkm", km);
                }
                return String.format("%.1fkm", km);
            }

            return String.format("%.0fm", meters);
        }
    }
}