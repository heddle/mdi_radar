package edu.cnu.mdi.radar.item;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.item.AItem;
import edu.cnu.mdi.item.ItemModification.ModificationType;
import edu.cnu.mdi.item.Layer;
import edu.cnu.mdi.mapping.MapView2D;
import edu.cnu.mdi.mapping.container.MapContainer;
import edu.cnu.mdi.mapping.graphics.MapGraphics;
import edu.cnu.mdi.mapping.graphics.MapGraphics.ProjectedMapShape;
import edu.cnu.mdi.mapping.projection.IMapProjection;
import edu.cnu.mdi.radar.geo.Geodesy;
import edu.cnu.mdi.radar.radar.RadarParameters;

/**
 * A map-native radar coverage item.
 *
 * <p>
 * A {@code RadarItem} represents one radar site on a {@link MapContainer}. The
 * item stores its true geometry in geographic coordinates: site longitude,
 * site latitude, range, azimuth, and angular fan width. The displayed fan is
 * rebuilt from those geographic quantities whenever the item is drawn. This is
 * important for map projections, because the projected shape may split at a
 * projection seam and cannot safely be treated as an ordinary world-coordinate
 * {@link Path2D}.
 * </p>
 *
 * <h2>Coordinate convention</h2>
 * <p>
 * Geographic points follow the MDI mapping convention:
 * </p>
 * <ul>
 *   <li>{@code x = longitude}</li>
 *   <li>{@code y = latitude}</li>
 *   <li>angles are stored internally in radians</li>
 * </ul>
 *
 * <h2>Radar fan rendering</h2>
 * <p>
 * The item draws:
 * </p>
 * <ol>
 *   <li>a translucent fan or circular coverage outline,</li>
 *   <li>colored radial line-of-sight rays,</li>
 *   <li>a small site marker at the radar focus.</li>
 * </ol>
 *
 * <h2>Line-of-sight convention</h2>
 * <p>
 * ETOPO elevations below sea level are bathymetry. They describe the sea floor,
 * not the surface that a radar beam must clear. Therefore this item treats
 * negative ETOPO elevations as {@code 0 m MSL} for LOS obstruction purposes.
 * This prevents ship radars over water from developing artificial notches due
 * to ocean depth. Terrain above sea level is used normally.
 * </p>
 *
 * <h2>Interaction</h2>
 * <p>
 * Radar items are draggable and selectable. Sector radars are rotatable.
 * Circular 360-degree radars are not rotatable because azimuth has no visual
 * meaning for a full circular fan. Radar items are intentionally not resizable;
 * their range and fan width come from {@link RadarParameters}.
 * </p>
 */
@SuppressWarnings("serial")
public class RadarItem extends AItem {

    // ---------------------------------------------------------------------
    // Drawing and hit-test constants
    // ---------------------------------------------------------------------

    /** Number of arc intervals used to approximate a sector fan edge. */
    private static final int ARC_STEPS = 48;

    /** Radius, in pixels, of the drawn radar-site focus marker. */
    private static final int FOCUS_RADIUS_PIX = 5;

    /** Additional hit-test tolerance, in pixels, around the focus and fan edge. */
    private static final int HIT_TOL_PIX = 5;

    /** Feedback color prefix used by the MDI feedback system. */
    private static final String FB_COLOR = "$azure$";

    /** Bearing step, in degrees, used when drawing colored LOS rays. */
    private static final double BEARING_STEP_DEG = 1.0;

    /** Range step, in metres, used when sampling terrain along LOS rays. */
    private static final double RANGE_STEP_M = 5_000.0;

    /**
     * Base target altitude thresholds, in metres AGL.
     *
     * <p>
     * These values are multiplied by the view's target-altitude scale before
     * classifying a ray segment. The first color that can see the sampled point
     * is used.
     * </p>
     */
    private static final double[] BASE_TARGET_ALTITUDES_M = {
            0.0,
            500.0,
            2_000.0,
            5_000.0,
            10_000.0
    };

    /** Alpha value used for colored target-altitude rays. */
    private static final int TARGET_ALPHA = 128;

    /**
     * Colors corresponding to {@link #BASE_TARGET_ALTITUDES_M}.
     *
     * <p>
     * The colors progress from low-altitude visibility to high-altitude
     * visibility. A point requiring more than the highest configured altitude is
     * drawn using {@link #MASKED_COLOR}.
     * </p>
     */
    private static final Color[] TARGET_ALTITUDE_COLORS = {
            new Color(0, 130, 0, TARGET_ALPHA),       // near-ground / surface
            new Color(70, 170, 60, TARGET_ALPHA),     // low
            new Color(230, 190, 40, TARGET_ALPHA),    // medium
            new Color(220, 110, 40, TARGET_ALPHA),    // high
            new Color(180, 40, 160, TARGET_ALPHA)     // very high
    };

    /** Color used when none of the configured target altitudes can see a point. */
    private static final Color MASKED_COLOR = new Color(40, 40, 40, 120);

    // ---------------------------------------------------------------------
    // Instance state
    // ---------------------------------------------------------------------

    /** Radar parameters represented by this item. */
    private final RadarParameters parameters;

    /**
     * Radar site location.
     *
     * <p>
     * Uses the MDI geographic convention: {@code x = longitude},
     * {@code y = latitude}, both in radians.
     * </p>
     */
    private final Point2D.Double siteLatLon = new Point2D.Double();

    /**
     * Cached projected fan from the last draw.
     *
     * <p>
     * This cache is used for hit testing. It is rebuilt during drawing and may
     * also be rebuilt by bounds computations.
     * </p>
     */
    private ProjectedMapShape lastProjectedFan;

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    /**
     * Creates a radar item at the supplied geographic site.
     *
     * @param layer      the layer that owns this item
     * @param parameters radar parameters; must not be {@code null}
     * @param latDeg     site latitude in decimal degrees
     * @param lonDeg     site longitude in decimal degrees
     * @throws IllegalArgumentException if {@code parameters} is {@code null}
     */
    public RadarItem(Layer layer, RadarParameters parameters,
                     double latDeg, double lonDeg) {
        super(layer);

        if (parameters == null) {
            throw new IllegalArgumentException("parameters cannot be null");
        }

        this.parameters = parameters;
        this.siteLatLon.x = Math.toRadians(lonDeg);
        this.siteLatLon.y = Math.toRadians(latDeg);

        setLocked(false);
        setDraggable(true);
        setRotatable(parameters.azimuthWidthDeg() < 359.9);
        setResizable(false);
        setDeletable(true);
        setSelectable(true);
        setConnectable(false);

        // The radar marker is drawn explicitly, so the inherited focus-fill
        // marker would be redundant and visually distracting.
        setFillFocus(false);

        getStyleSafe().setLineColor(new Color(255, 170, 40));
        getStyleSafe().setFillColor(new Color(255, 170, 40, 64));
        getStyleSafe().setLineWidth(1.5f);

        setDisplayName(parameters.shortName());
    }

    // ---------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------

    /**
     * Draws the radar fan, colored LOS rays, and focus marker.
     *
     * <p>
     * The fan is built as a geographic polygon and projected through
     * {@link MapGraphics}, allowing projections with seams to split the fan into
     * safe subpaths. The LOS rays are drawn after the translucent fan fill and
     * before the fan outline.
     * </p>
     *
     * @param g2        graphics context
     * @param container rendering container
     */
    @Override
    public void drawItem(Graphics2D g2, IContainer container) {
        if (!(container instanceof MapContainer mapContainer)) {
            return;
        }

        lastProjectedFan = buildProjectedFan(mapContainer);

        Color fillColor = getStyleSafe().getFillColor();
        Color lineColor = getStyleSafe().getLineColor();

        Composite oldComposite = g2.getComposite();
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();

        try {
            if (lastProjectedFan != null && !lastProjectedFan.isEmpty()) {
                if (fillColor != null) {
                    g2.setColor(fillColor);
                    for (Path2D.Double path : lastProjectedFan.getPaths()) {
                        g2.fill(path);
                    }
                }

                drawVisibilityRays(g2, mapContainer);

                if (lineColor != null) {
                    g2.setColor(lineColor);
                    g2.setStroke(new BasicStroke(getStyleSafe().getLineWidth()));
                    for (Path2D.Double path : lastProjectedFan.getPaths()) {
                        g2.draw(path);
                    }
                }
            }

            drawFocusCircle(g2, mapContainer);
        } finally {
            g2.setComposite(oldComposite);
            g2.setColor(oldColor);
            g2.setStroke(oldStroke);
        }
    }

    /**
     * Draws colored radial LOS rays across the radar fan.
     *
     * <p>
     * For each sampled bearing, this method marches outward in range. At each
     * sample point it determines the lowest target-altitude band that has
     * line of sight from the radar to that point. Segments are colored according
     * to that required target altitude.
     * </p>
     *
     * <p>
     * Negative ETOPO elevations are treated as sea surface height
     * ({@code 0 m MSL}) for LOS purposes. Bathymetry is not an obstruction.
     * </p>
     *
     * @param g2        graphics context
     * @param container map container
     */
    private void drawVisibilityRays(Graphics2D g2, MapContainer container) {
        if (!(container.getView() instanceof MapView2D mapView)) {
            return;
        }

        double siteTerrainM = mapView.getElevation(
                Math.toDegrees(siteLatLon.y),
                Math.toDegrees(siteLatLon.x));

        if (!Double.isFinite(siteTerrainM)) {
            siteTerrainM = 0.0;
        }

        /*
         * For LOS purposes, use the physical obstruction/surface height rather
         * than raw ETOPO bathymetry. A ship over -140 m water is at the sea
         * surface, not 140 m below it.
         */
        double siteSurfaceM = losSurfaceHeightM(siteTerrainM);
        double antennaMslM = siteSurfaceM + parameters.antennaHeightAglM();
        double minElevationRad = Math.toRadians(parameters.minElevationDeg());

        double scale = 1.0;
        if (mapView instanceof edu.cnu.mdi.radar.ui.RadarView radarView) {
            scale = radarView.getTargetAltitudeScale();
        }

        double widthDeg = parameters.azimuthWidthDeg();
        double maxRangeM = parameters.maxRangeM();

        double startDeg;
        double endDeg;

        if (widthDeg >= 359.9) {
            startDeg = 0.0;
            endDeg = 360.0;
        } else {
            startDeg = getAzimuth() - widthDeg / 2.0;
            endDeg = getAzimuth() + widthDeg / 2.0;
        }

        Stroke oldStroke = g2.getStroke();
        Color oldColor = g2.getColor();

        try {
            g2.setStroke(new BasicStroke(
                    2.0f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));

            for (double bearingDeg = startDeg;
                    bearingDeg <= endDeg + 0.001;
                    bearingDeg += BEARING_STEP_DEG) {
                drawVisibilityRay(
                        g2,
                        container,
                        mapView,
                        bearingDeg,
                        maxRangeM,
                        antennaMslM,
                        minElevationRad,
                        scale);
            }
        } finally {
            g2.setStroke(oldStroke);
            g2.setColor(oldColor);
        }
    }

    /**
     * Draws one colored LOS ray at a fixed bearing.
     *
     * <p>
     * The algorithm keeps a running maximum of the terrain/curvature blocking
     * angle. At each range sample it computes the target height required to clear
     * that blocking angle, converts it to AGL at the sampled surface, and colors
     * the segment using the configured altitude bands.
     * </p>
     *
     * @param g2              graphics context
     * @param container       map container
     * @param mapView         owning map view
     * @param bearingDeg      ray bearing in degrees, 0 = north, clockwise
     * @param maxRangeM       maximum radar range in metres
     * @param antennaMslM     antenna phase-centre height in metres MSL
     * @param minElevationRad minimum radar elevation angle in radians
     * @param altitudeScale   scale factor applied to target-altitude thresholds
     */
    private void drawVisibilityRay(Graphics2D g2,
                                   MapContainer container,
                                   MapView2D mapView,
                                   double bearingDeg,
                                   double maxRangeM,
                                   double antennaMslM,
                                   double minElevationRad,
                                   double altitudeScale) {

        Point prevPoint = getFocusPoint(container);
        if (prevPoint == null) {
            return;
        }

        /*
         * The minimum vertical angle the beam must clear so far. This begins at
         * the radar's own minimum elevation angle and only increases when terrain
         * produces a larger required elevation.
         */
        double blockingAngleRad = minElevationRad;

        Point2D.Double sampleLatLon = new Point2D.Double();

        for (double rangeM = RANGE_STEP_M; rangeM <= maxRangeM; rangeM += RANGE_STEP_M) {
            Geodesy.destination(
                    siteLatLon.y,
                    siteLatLon.x,
                    Math.toRadians(bearingDeg),
                    rangeM,
                    sampleLatLon);

            double latDeg = Math.toDegrees(sampleLatLon.y);
            double lonDeg = Math.toDegrees(sampleLatLon.x);

            double terrainM = mapView.getElevation(latDeg, lonDeg);
            if (!Double.isFinite(terrainM)) {
                prevPoint = null;
                continue;
            }

            /*
             * Critical correction:
             *
             * ETOPO elevations below sea level are ocean depth, not an
             * obstruction height. For LOS, water contributes a surface height of
             * 0 m MSL. Land elevations are used normally.
             */
            double surfaceM = losSurfaceHeightM(terrainM);

            /*
             * Terrain itself may raise the blocking angle. Over open water this
             * reduces to the sea-surface curvature/refraction calculation.
             */
            double terrainAngleRad = Geodesy.elevationAngle(
                    antennaMslM,
                    surfaceM,
                    rangeM);

            blockingAngleRad = Math.max(blockingAngleRad, terrainAngleRad);

            /*
             * Height MSL required at this range to clear the current blocker.
             */
            double requiredTargetMslM = Geodesy.targetHeightForElevationAngle(
                    antennaMslM,
                    rangeM,
                    blockingAngleRad);

            /*
             * Convert required target height to AGL at the sampled surface. Over
             * water, "AGL" is effectively height above sea surface because
             * surfaceM has been clamped to 0 m MSL.
             */
            double requiredTargetAglM = Math.max(0.0, requiredTargetMslM - surfaceM);

            Color segmentColor = colorForRequiredTargetAgl(
                    requiredTargetAglM,
                    altitudeScale);

            Point2D.Double xy = projectLatLon(container, sampleLatLon);
            if (xy == null) {
                prevPoint = null;
                continue;
            }

            Point p = new Point();
            container.worldToLocal(p, xy);

            if (prevPoint != null) {
                g2.setColor(segmentColor);
                g2.drawLine(prevPoint.x, prevPoint.y, p.x, p.y);
            }

            prevPoint = p;
        }
    }

    /**
     * Converts an ETOPO elevation to the physical surface height used for
     * line-of-sight obstruction calculations.
     *
     * <p>
     * Positive values are land elevations and are preserved. Negative values are
     * bathymetry and are clamped to sea level. This prevents the ocean floor from
     * incorrectly behaving as the obstruction surface for radar LOS.
     * </p>
     *
     * @param etopoElevationM raw ETOPO elevation in metres
     * @return obstruction/surface height in metres MSL
     */
    private static double losSurfaceHeightM(double etopoElevationM) {
        return Math.max(0.0, etopoElevationM);
    }

    /**
     * Chooses the color for a ray segment based on the required target altitude.
     *
     * @param requiredAglM  required target height in metres AGL
     * @param altitudeScale scale factor applied to the configured altitude bands
     * @return color for the segment
     */
    private static Color colorForRequiredTargetAgl(double requiredAglM,
                                                   double altitudeScale) {
        for (int i = 0; i < BASE_TARGET_ALTITUDES_M.length; i++) {
            double threshold = BASE_TARGET_ALTITUDES_M[i] * altitudeScale;
            if (requiredAglM <= threshold) {
                return TARGET_ALTITUDE_COLORS[i];
            }
        }

        return MASKED_COLOR;
    }

    // ---------------------------------------------------------------------
    // Visibility, hit testing, and bounds
    // ---------------------------------------------------------------------

    /**
     * Tests whether this item should be drawn in the current graphics clip.
     *
     * @param g2        graphics context
     * @param container rendering container
     * @return {@code true} if the item should be drawn
     */
    @Override
    public boolean shouldDraw(Graphics2D g2, IContainer container) {
        Rectangle bounds = getBounds(container);
        if (bounds == null) {
            return false;
        }

        Rectangle clip = g2.getClipBounds();
        if (clip == null) {
            return true;
        }

        return clip.intersects(bounds);
    }

    /**
     * Tests whether the radar item contains or is near a screen point.
     *
     * <p>
     * The focus marker is tested first. The projected fan is then tested as a
     * filled polygon and finally as an outline with tolerance.
     * </p>
     *
     * @param container   rendering container
     * @param screenPoint point in screen coordinates
     * @return {@code true} if the point hits the item
     */
    @Override
    public boolean contains(IContainer container, Point screenPoint) {
        if (screenPoint == null) {
            return false;
        }

        Point fp = getFocusPoint(container);
        if (fp != null && fp.distance(screenPoint) <= FOCUS_RADIUS_PIX + HIT_TOL_PIX) {
            return true;
        }

        if (lastProjectedFan != null) {
            if (MapGraphics.contains(lastProjectedFan, screenPoint)) {
                return true;
            }
            return MapGraphics.isPointNear(lastProjectedFan, screenPoint, HIT_TOL_PIX);
        }

        Rectangle bounds = getBounds(container);
        return bounds != null && bounds.contains(screenPoint);
    }

    /**
     * Returns the screen-coordinate bounds of the projected fan and focus marker.
     *
     * @param container rendering container
     * @return screen bounds, or {@code null} if the item cannot be projected
     */
    @Override
    public Rectangle getBounds(IContainer container) {
        if (!(container instanceof MapContainer mapContainer)) {
            return null;
        }

        ProjectedMapShape fan = buildProjectedFan(mapContainer);
        Rectangle r = (fan == null) ? new Rectangle() : fan.getBounds();

        Point fp = getFocusPoint(container);
        if (fp != null) {
            r.add(new Rectangle(
                    fp.x - FOCUS_RADIUS_PIX,
                    fp.y - FOCUS_RADIUS_PIX,
                    2 * FOCUS_RADIUS_PIX,
                    2 * FOCUS_RADIUS_PIX));
        }

        return r;
    }

    /**
     * Returns an approximate world-coordinate bounding rectangle.
     *
     * <p>
     * {@code RadarItem} is map-native, so its authoritative geometry is
     * geographic, not ordinary world geometry. This method converts the current
     * screen bounds back to world coordinates and is intended only as a rough
     * framework compatibility bound.
     * </p>
     *
     * @return approximate world bounds
     */
    @Override
    public Rectangle2D.Double getWorldBounds() {
        IContainer container = getContainer();
        Rectangle b = getBounds(container);
        if (b == null || container == null) {
            return new Rectangle2D.Double();
        }

        Point2D.Double w0 = new Point2D.Double();
        Point2D.Double w1 = new Point2D.Double();

        container.localToWorld(new Point(b.x, b.y), w0);
        container.localToWorld(new Point(b.x + b.width, b.y + b.height), w1);

        return new Rectangle2D.Double(
                Math.min(w0.x, w1.x),
                Math.min(w0.y, w1.y),
                Math.abs(w1.x - w0.x),
                Math.abs(w1.y - w0.y));
    }

    // ---------------------------------------------------------------------
    // Focus, selection, and rotation handles
    // ---------------------------------------------------------------------

    /**
     * Returns the screen-coordinate focus point for the radar site.
     *
     * @param container rendering container
     * @return focus point in screen coordinates, or {@code null}
     */
    @Override
    public Point getFocusPoint(IContainer container) {
        if (!(container instanceof MapContainer mapContainer)) {
            return null;
        }

        Point2D.Double xy = projectSite(mapContainer);
        if (xy == null) {
            return null;
        }

        Point pp = new Point();
        container.worldToLocal(pp, xy);
        return pp;
    }

    /**
     * Returns the projected world-coordinate focus point.
     *
     * <p>
     * This method returns the projected map XY position of the radar site, not
     * the geographic longitude/latitude. Use {@link #getSiteLatLon()} for the
     * geographic location.
     * </p>
     *
     * @return projected focus point, or {@code null}
     */
    @Override
    public Point2D.Double getFocus() {
        IContainer container = getContainer();
        if (container instanceof MapContainer mapContainer) {
            return projectSite(mapContainer);
        }
        return null;
    }

    /**
     * Returns the rotation-handle point for sector radars.
     *
     * <p>
     * Circular radars are non-rotatable, so this method returns {@code null} for
     * those items. For sector radars, the handle is placed along the boresight at
     * 80% of the maximum range.
     * </p>
     *
     * @param container rendering container
     * @return rotation handle in screen coordinates, or {@code null}
     */
    @Override
    public Point getRotatePoint(IContainer container) {
        if (!isRotatable() || !(container instanceof MapContainer mapContainer)) {
            return null;
        }

        double handleRangeM = 0.80 * parameters.maxRangeM();
        Point2D.Double handleLatLon = new Point2D.Double();

        Geodesy.destination(
                siteLatLon.y,
                siteLatLon.x,
                Math.toRadians(getAzimuth()),
                handleRangeM,
                handleLatLon);

        Point2D.Double xy = projectLatLon(mapContainer, handleLatLon);
        if (xy == null) {
            return null;
        }

        Point pp = new Point();
        container.worldToLocal(pp, xy);
        return pp;
    }

    /**
     * Returns selection points for the radar item.
     *
     * <p>
     * The site focus is always used as a selection point. Sector radars also
     * include a rotation handle.
     * </p>
     *
     * @param container rendering container
     * @return selection points, or {@code null}
     */
    @Override
    public Point[] getSelectionPoints(IContainer container) {
        Point fp = getFocusPoint(container);
        Point rp = getRotatePoint(container);

        if (fp == null) {
            return null;
        }

        if (rp == null) {
            return new Point[] { fp };
        }

        return new Point[] { fp, rp };
    }

    // ---------------------------------------------------------------------
    // Modification
    // ---------------------------------------------------------------------

    /**
     * Translates the item by a world-coordinate delta.
     *
     * <p>
     * This method is intentionally a no-op. Geographic dragging is handled in
     * {@link #modify()} using screen coordinates and the active map projection,
     * because directly translating projected world coordinates would be
     * projection-dependent.
     * </p>
     *
     * @param dx world-coordinate x delta
     * @param dy world-coordinate y delta
     */
    @Override
    public void translateWorld(double dx, double dy) {
        // Geographic item: drag through modify(), not by projected-world delta.
    }

    /**
     * Continues an interactive drag or rotate operation.
     *
     * <p>
     * Dragging keeps the original focus-to-mouse offset in screen coordinates and
     * converts the new focus point back to geographic coordinates. Rotation
     * converts the current mouse point to geographic coordinates and computes the
     * great-circle initial azimuth from the radar site.
     * </p>
     */
    @Override
    public void modify() {
        if (_modification == null) {
            return;
        }

        IContainer container = _modification.getContainer();
        if (!(container instanceof MapContainer mapContainer)) {
            return;
        }

        if (_modification.getType() == ModificationType.DRAG) {
            Point startFocusPoint = _modification.getStartFocusPoint();
            Point startMouse = _modification.getStartMousePoint();
            Point currentMouse = _modification.getCurrentMousePoint();

            if (startFocusPoint == null || startMouse == null || currentMouse == null) {
                return;
            }

            Point newFocusPoint = new Point(
                    startFocusPoint.x + currentMouse.x - startMouse.x,
                    startFocusPoint.y + currentMouse.y - startMouse.y);

            Point2D.Double newLatLon = screenToLatLon(mapContainer, newFocusPoint);
            if (newLatLon != null) {
                siteLatLon.x = newLatLon.x;
                siteLatLon.y = newLatLon.y;
            }
        } else if (_modification.getType() == ModificationType.ROTATE) {
            Point2D.Double mouseLatLon = screenToLatLon(
                    mapContainer,
                    _modification.getCurrentMousePoint());

            if (mouseLatLon != null) {
                double az = MapGraphics.greatCircleAzimuth(siteLatLon, mouseLatLon);
                if (Double.isFinite(az)) {
                    setAzimuth(Math.toDegrees(az));
                }
            }
        }

        geometryChanged();
        container.refresh();
    }

    // ---------------------------------------------------------------------
    // Fan geometry
    // ---------------------------------------------------------------------

    /**
     * Builds the projected radar fan for the current map projection.
     *
     * @param container map container
     * @return projected, seam-aware fan shape
     */
    private ProjectedMapShape buildProjectedFan(MapContainer container) {
        Point2D.Double[] fan = buildFanLatLon();
        return MapGraphics.buildProjectedPolygon(container, fan, 1.0);
    }

    /**
     * Builds the geographic vertices of the radar fan.
     *
     * <p>
     * For sector radars, the polygon begins at the radar site and then follows
     * the arc edge from left to right. For 360-degree radars, the polygon is a
     * sampled range ring/disk.
     * </p>
     *
     * @return fan vertices in geographic radians, {@code x=lon}, {@code y=lat}
     */
    private Point2D.Double[] buildFanLatLon() {
        double width = parameters.azimuthWidthDeg();
        double rangeM = parameters.maxRangeM();

        if (width >= 359.9) {
            ArrayList<Point2D.Double> pts = new ArrayList<>();
            for (int i = 0; i < 96; i++) {
                double bearing = Math.toRadians(i * 360.0 / 96.0);
                Point2D.Double p = new Point2D.Double();
                Geodesy.destination(siteLatLon.y, siteLatLon.x, bearing, rangeM, p);
                pts.add(p);
            }
            return pts.toArray(Point2D.Double[]::new);
        }

        ArrayList<Point2D.Double> pts = new ArrayList<>();
        pts.add(new Point2D.Double(siteLatLon.x, siteLatLon.y));

        double start = getAzimuth() - width / 2.0;
        double step = width / ARC_STEPS;

        for (int i = 0; i <= ARC_STEPS; i++) {
            double bearingDeg = start + i * step;
            Point2D.Double p = new Point2D.Double();
            Geodesy.destination(
                    siteLatLon.y,
                    siteLatLon.x,
                    Math.toRadians(bearingDeg),
                    rangeM,
                    p);
            pts.add(p);
        }

        return pts.toArray(Point2D.Double[]::new);
    }

    /**
     * Draws the radar-site focus marker.
     *
     * @param g2        graphics context
     * @param container map container
     */
    private void drawFocusCircle(Graphics2D g2, MapContainer container) {
        Point pp = getFocusPoint(container);
        if (pp == null) {
            return;
        }

        Color lineColor = getStyleSafe().getLineColor();

        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillOval(
                pp.x - FOCUS_RADIUS_PIX,
                pp.y - FOCUS_RADIUS_PIX,
                2 * FOCUS_RADIUS_PIX,
                2 * FOCUS_RADIUS_PIX);

        g2.setColor(lineColor == null ? Color.ORANGE : lineColor);
        g2.drawOval(
                pp.x - FOCUS_RADIUS_PIX,
                pp.y - FOCUS_RADIUS_PIX,
                2 * FOCUS_RADIUS_PIX,
                2 * FOCUS_RADIUS_PIX);
    }

    // ---------------------------------------------------------------------
    // Projection helpers
    // ---------------------------------------------------------------------

    /**
     * Projects the radar site into map projection coordinates.
     *
     * @param container map container
     * @return projected site point, or {@code null}
     */
    private Point2D.Double projectSite(MapContainer container) {
        return projectLatLon(container, siteLatLon);
    }

    /**
     * Projects a geographic point into map projection coordinates.
     *
     * @param container map container
     * @param latLon    geographic point, radians, {@code x=lon}, {@code y=lat}
     * @return projected point, or {@code null} if not drawable
     */
    private Point2D.Double projectLatLon(MapContainer container,
                                         Point2D.Double latLon) {
        if (!(container.getView() instanceof MapView2D mapView)) {
            return null;
        }

        IMapProjection projection = mapView.getProjection();

        if (!projection.isPointVisible(latLon)) {
            return null;
        }

        Point2D.Double xy = new Point2D.Double();
        projection.latLonToXY(latLon, xy);

        if (!Double.isFinite(xy.x) || !Double.isFinite(xy.y)) {
            return null;
        }

        return xy;
    }

    /**
     * Converts a screen point to geographic longitude/latitude.
     *
     * @param container   map container
     * @param screenPoint point in screen coordinates
     * @return geographic point in radians, {@code x=lon}, {@code y=lat}, or
     *         {@code null}
     */
    private Point2D.Double screenToLatLon(MapContainer container,
                                          Point screenPoint) {
        if (screenPoint == null || !(container.getView() instanceof MapView2D mapView)) {
            return null;
        }

        Point2D.Double xy = new Point2D.Double();
        container.localToWorld(screenPoint, xy);

        Point2D.Double latLon = new Point2D.Double();
        mapView.getProjection().latLonFromXY(latLon, xy);

        if (!Double.isFinite(latLon.x) || !Double.isFinite(latLon.y)) {
            return null;
        }

        if (!mapView.getProjection().isPointVisible(latLon)) {
            return null;
        }

        return latLon;
    }

    // ---------------------------------------------------------------------
    // Public accessors
    // ---------------------------------------------------------------------

    /**
     * Returns the radar parameters represented by this item.
     *
     * @return radar parameters
     */
    public RadarParameters getRadarParameters() {
        return parameters;
    }

    /**
     * Returns the radar site location.
     *
     * @return copy of the site location in radians, {@code x=lon}, {@code y=lat}
     */
    public Point2D.Double getSiteLatLon() {
        return new Point2D.Double(siteLatLon.x, siteLatLon.y);
    }

    /**
     * Returns the site latitude in decimal degrees.
     *
     * @return site latitude, degrees
     */
    public double getSiteLatitudeDeg() {
        return Math.toDegrees(siteLatLon.y);
    }

    /**
     * Returns the site longitude in decimal degrees.
     *
     * @return site longitude, degrees
     */
    public double getSiteLongitudeDeg() {
        return Math.toDegrees(siteLatLon.x);
    }

    // ---------------------------------------------------------------------
    // Feedback
    // ---------------------------------------------------------------------

    /**
     * Adds a feedback string with the radar feedback color prefix.
     *
     * @param text            feedback text
     * @param feedbackStrings destination feedback list
     */
    private void addWithColor(String text, List<String> feedbackStrings) {
        if (text == null || text.isEmpty()) {
            return;
        }

        feedbackStrings.add(FB_COLOR + text);
    }
    
    /**
     * Returns a defensive copy of the base target-altitude thresholds used for
     * colored line-of-sight rays.
     *
     * <p>
     * The values are in metres AGL before applying the view-level altitude scale.
     * </p>
     *
     * @return base target-altitude thresholds, metres AGL
     */
    public static double[] getBaseTargetAltitudesM() {
        return BASE_TARGET_ALTITUDES_M.clone();
    }

    /**
     * Returns a defensive copy of the colors used for target-altitude ray bands.
     *
     * @return target-altitude colors
     */
    public static Color[] getTargetAltitudeColors() {
        return TARGET_ALTITUDE_COLORS.clone();
    }

    /**
     * Returns the color used when a point requires a target altitude above the
     * highest configured threshold.
     *
     * @return masked color
     */
    public static Color getMaskedColor() {
        return MASKED_COLOR;
    }

    /**
     * Adds radar-specific feedback when the mouse is over this item.
     *
     * @param container       rendering container
     * @param pp              screen point
     * @param wp              world point
     * @param feedbackStrings destination feedback list
     */
    @Override
    public void getFeedbackStrings(IContainer container,
                                   Point pp,
                                   Point2D.Double wp,
                                   List<String> feedbackStrings) {
        if (pp == null || wp == null) {
            return;
        }

        if (contains(container, pp)) {
            String[] strArray = parameters.toStringArray();
            for (String s : strArray) {
                addWithColor(s, feedbackStrings);
            }

            addWithColor(
                    String.format(
                            "site Lat/Lon %.4f, %.4f",
                            Math.toDegrees(siteLatLon.y),
                            Math.toDegrees(siteLatLon.x)),
                    feedbackStrings);

            addWithColor(
                    String.format("boresight azimuth %.1f deg", getAzimuth()),
                    feedbackStrings);
        }
    }
}