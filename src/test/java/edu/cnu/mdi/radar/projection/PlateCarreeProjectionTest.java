package edu.cnu.mdi.radar.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;

import org.junit.jupiter.api.Test;

import edu.cnu.mdi.mapping.theme.MapTheme;

class PlateCarreeProjectionTest {

    private static final double TOLERANCE = 1.0e-12;

    @Test
    void roundTripsCoordinatesAroundConfiguredCenter() {
        PlateCarreeProjection projection = new PlateCarreeProjection(
                MapTheme.light(), Math.toRadians(180.0));
        Point2D.Double geographic = new Point2D.Double(
                Math.toRadians(-150.0), Math.toRadians(42.0));
        Point2D.Double projected = new Point2D.Double();
        Point2D.Double restored = new Point2D.Double();

        projection.latLonToXY(geographic, projected);
        projection.latLonFromXY(restored, projected);

        assertEquals(0.0,
                projection.wrapLongitude(restored.x - geographic.x), TOLERANCE);
        assertEquals(geographic.y, restored.y, TOLERANCE);
    }

    @Test
    void advertisesApplicationAndPeriodicBehavior() {
        PlateCarreeProjection projection =
                new PlateCarreeProjection(MapTheme.light());
        assertNull(projection.getProjection());
        assertEquals("Plate Carrée", projection.name());
        assertTrue(projection.isLongitudePeriodic());
        assertTrue(projection.supportsRecenter());
    }

    @Test
    void recenterChangesCentralLongitudeAndRejectsInvalidInput() {
        PlateCarreeProjection projection =
                new PlateCarreeProjection(MapTheme.light());
        assertTrue(projection.recenterOn(new Point2D.Double(1.25, 0.4)));
        assertEquals(1.25, projection.getCentralLongitude(), TOLERANCE);
        assertFalse(projection.recenterOn(new Point2D.Double(Double.NaN, 0.0)));
    }
}
