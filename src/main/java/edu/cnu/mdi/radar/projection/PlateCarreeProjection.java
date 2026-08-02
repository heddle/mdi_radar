package edu.cnu.mdi.radar.projection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import edu.cnu.mdi.container.IContainer;
import edu.cnu.mdi.mapping.projection.EProjection;
import edu.cnu.mdi.mapping.projection.IMapProjection;
import edu.cnu.mdi.mapping.theme.MapTheme;

/**
 * Application-supplied Plate Carrée (equidistant cylindrical) projection.
 *
 * <p>The forward equations are {@code x = λ - λ0} and {@code y = φ}, with
 * longitude wrapped about the configurable central meridian. This deliberately
 * lives in the radar application rather than MDI's built-in projection package,
 * demonstrating the {@link IMapProjection} extension point.</p>
 */
public final class PlateCarreeProjection implements IMapProjection {

    private static final double MIN_LAT = -Math.PI / 2.0;
    private static final double MAX_LAT = Math.PI / 2.0;
    private static final Rectangle2D.Double BOUNDS =
            new Rectangle2D.Double(-Math.PI, MIN_LAT, 2.0 * Math.PI, Math.PI);

    private MapTheme theme;
    private double centralLongitude;

    /** Creates a prime-meridian-centered projection using the supplied theme. */
    public PlateCarreeProjection(MapTheme theme) {
        this(theme, 0.0);
    }

    /** Creates a projection with the supplied theme and central longitude. */
    public PlateCarreeProjection(MapTheme theme, double centralLongitude) {
        setTheme(theme);
        setCentralLongitude(centralLongitude);
    }

    @Override
    public void latLonToXY(Point2D.Double latLon, Point2D.Double xy) {
        if (!isPointVisible(latLon)) {
            xy.setLocation(Double.NaN, Double.NaN);
            return;
        }
        xy.setLocation(wrapLongitude(latLon.x - centralLongitude), latLon.y);
    }

    @Override
    public void latLonFromXY(Point2D.Double latLon, Point2D.Double xy) {
        if (!isPointOnMap(xy)) {
            latLon.setLocation(Double.NaN, Double.NaN);
            return;
        }
        latLon.setLocation(wrapLongitude(xy.x + centralLongitude), xy.y);
    }

    @Override
    public boolean isPointVisible(Point2D.Double latLon) {
        return latLon != null
                && Double.isFinite(latLon.x)
                && Double.isFinite(latLon.y)
                && latLon.y >= MIN_LAT
                && latLon.y <= MAX_LAT;
    }

    @Override
    public boolean isPointOnMap(Point2D.Double xy) {
        return xy != null
                && Double.isFinite(xy.x)
                && Double.isFinite(xy.y)
                && xy.x >= -Math.PI
                && xy.x <= Math.PI
                && xy.y >= MIN_LAT
                && xy.y <= MAX_LAT;
    }

    @Override
    public void drawMapOutline(Graphics2D g2, IContainer container) {
        drawShape(g2, projectedBoundary(container), theme.getOutlineColor(),
                theme.getOutlineStrokeWidth());
    }

    @Override
    public void drawLatitudeLine(Graphics2D g2, IContainer container, double latitude) {
        if (!Double.isFinite(latitude) || latitude < MIN_LAT || latitude > MAX_LAT) {
            return;
        }
        Point left = toLocal(container, -Math.PI, latitude);
        Point right = toLocal(container, Math.PI, latitude);
        drawSegment(g2, left, right);
    }

    @Override
    public void drawLongitudeLine(Graphics2D g2, IContainer container, double longitude) {
        if (!Double.isFinite(longitude)) {
            return;
        }
        double x = wrapLongitude(longitude - centralLongitude);
        Point bottom = toLocal(container, x, MIN_LAT);
        Point top = toLocal(container, x, MAX_LAT);
        drawSegment(g2, bottom, top);
    }

    @Override
    public Shape createClipShape(IContainer container) {
        return projectedBoundary(container);
    }

    @Override
    public EProjection getProjection() {
        return null;
    }

    @Override
    public String name() {
        return "Plate Carrée";
    }

    @Override
    public Rectangle2D.Double getXYBounds() {
        return new Rectangle2D.Double(BOUNDS.x, BOUNDS.y, BOUNDS.width, BOUNDS.height);
    }

    @Override
    public MapTheme getTheme() {
        return theme;
    }

    @Override
    public void setTheme(MapTheme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
    }

    @Override
    public boolean crossesSeam(double longitude1, double longitude2) {
        double x1 = wrapLongitude(longitude1 - centralLongitude);
        double x2 = wrapLongitude(longitude2 - centralLongitude);
        return Math.abs(x1 - x2) > Math.PI;
    }

    @Override
    public boolean isLongitudePeriodic() {
        return true;
    }

    @Override
    public boolean supportsRecenter() {
        return true;
    }

    @Override
    public boolean recenterOn(Point2D.Double latLon) {
        if (latLon == null || !Double.isFinite(latLon.x)) {
            return false;
        }
        setCentralLongitude(latLon.x);
        return true;
    }

    /** Returns the central longitude in radians. */
    public double getCentralLongitude() {
        return centralLongitude;
    }

    /** Sets the central longitude in radians. */
    public void setCentralLongitude(double longitude) {
        if (!Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Central longitude must be finite");
        }
        centralLongitude = wrapLongitude(longitude);
    }

    private Shape projectedBoundary(IContainer container) {
        Point lowerLeft = toLocal(container, -Math.PI, MIN_LAT);
        Point lowerRight = toLocal(container, Math.PI, MIN_LAT);
        Point upperRight = toLocal(container, Math.PI, MAX_LAT);
        Point upperLeft = toLocal(container, -Math.PI, MAX_LAT);

        Path2D path = new Path2D.Double();
        path.moveTo(lowerLeft.x, lowerLeft.y);
        path.lineTo(lowerRight.x, lowerRight.y);
        path.lineTo(upperRight.x, upperRight.y);
        path.lineTo(upperLeft.x, upperLeft.y);
        path.closePath();
        return path;
    }

    private void drawSegment(Graphics2D g2, Point start, Point end) {
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        g2.setColor(theme.getGraticuleColor());
        g2.setStroke(new BasicStroke(theme.getGraticuleStrokeWidth()));
        g2.drawLine(start.x, start.y, end.x, end.y);
        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    private static void drawShape(Graphics2D g2, Shape shape, Color color, float width) {
        Color oldColor = g2.getColor();
        Stroke oldStroke = g2.getStroke();
        g2.setColor(color);
        g2.setStroke(new BasicStroke(width));
        g2.draw(shape);
        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    private static Point toLocal(IContainer container, double x, double y) {
        Point point = new Point();
        container.worldToLocal(point, new Point2D.Double(x, y));
        return point;
    }
}
