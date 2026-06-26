package edu.cnu.mdi.radar.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Objects;

import javax.swing.AbstractButton;
import javax.swing.ToolTipManager;

import edu.cnu.mdi.component.ButtonPainter;
import edu.cnu.mdi.component.DrawnToggleButton;
import edu.cnu.mdi.component.LabeledButtonContainer;
import edu.cnu.mdi.graphics.toolbar.BaseToolBar;
import edu.cnu.mdi.radar.radar.RadarBasing;
import edu.cnu.mdi.radar.radar.RadarParameters;

/**
 * Toggle button used by the radar demo palette.
 *
 * <p>The button draws a small animated radar symbol based on the radar basing
 * type. Ground-based radars are shown as a sector/fan radar. Ship-based radars
 * are shown as a small ship with a rotating circular sweep.</p>
 */
@SuppressWarnings("serial")
public class RadarButton extends DrawnToggleButton implements MouseListener {

    private static final Dimension PREFERRED_SIZE = new Dimension(32, 32);

    // The radar parameters represented by this button.
    private final RadarParameters parameters;
    
    private final RadarView view;
    
    private final BaseToolBar toolBar;

    
    // The labeled button container that holds this button and its label.
    private LabeledButtonContainer labeledButtonContainer;

    /**
     * Creates an animated radar palette button.
     *
     * @param parameters radar parameters represented by this button
     */
    public RadarButton(RadarView view, BaseToolBar toolBar, RadarParameters parameters) {
        super(true, painterFor(Objects.requireNonNull(parameters, "parameters")));
        this.view = view;
        this.toolBar = toolBar;
        this.parameters = parameters;

        setToolTipText(parameters.name());
        ToolTipManager.sharedInstance().registerComponent(this);
    }
    
    /**
	 * Returns the labeled button container that holds this button and its label.
	 *
	 * @return the labeled button container
	 */
    public LabeledButtonContainer getLabeledButtonContainer() {
		if (labeledButtonContainer == null) {
			labeledButtonContainer = new LabeledButtonContainer(this, parameters.shortName());
		}
		return labeledButtonContainer;
	}

    /**
     * Returns the radar parameters represented by this button.
     *
     * @return radar parameters
     */
    public RadarParameters getRadarParameters() {
        return parameters;
    }

    private static ButtonPainter painterFor(RadarParameters parameters) {
        if (parameters.basing() == RadarBasing.SHIP) {
            return new ShipRadarPainter();
        }

        return new GroundRadarPainter();
    }

    /**
     * Painter for ground-based sector radars.
     */
    private static final class GroundRadarPainter implements ButtonPainter {

        @Override
        public Dimension getPreferredSize() {
            return PREFERRED_SIZE;
        }

        @Override
        public void draw(Graphics2D g2, AbstractButton button,
                         Rectangle bounds, long frameCount) {

            prepare(g2);

            int size = Math.min(bounds.width, bounds.height);
            int pad = Math.max(3, size / 10);

            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY() + size * 0.18;
            double radius = size * 0.65;

            boolean selected = button.isSelected();
            boolean rollover = button.getModel().isRollover();
            boolean pressed = button.getModel().isPressed();

            Color outline = stateColor(button, selected, rollover, pressed);
            Color fill0 = new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 90);
            Color fill1 = new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 15);

            double sweep = 55.0 + 20.0 * Math.sin(frameCount * 0.16);
            double start = 90.0 - sweep / 2.0;

            Arc2D.Double fan = new Arc2D.Double(
                    cx - radius,
                    cy - radius,
                    2.0 * radius,
                    2.0 * radius,
                    start,
                    sweep,
                    Arc2D.PIE);

            g2.setPaint(new RadialGradientPaint(
                    new Point((int) cx, (int) cy),
                    (float) radius,
                    new float[] { 0.0f, 1.0f },
                    new Color[] { fill0, fill1 }));
            g2.fill(fan);

            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(outline);
            g2.draw(fan);

            // Antenna mast / site marker.
            double mastTopY = cy - size * 0.10;
            double mastBottomY = bounds.y + bounds.height - pad - 3;

            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(cx, mastTopY, cx, mastBottomY));

            Polygon base = new Polygon();
            base.addPoint((int) cx, (int) mastTopY);
            base.addPoint((int) (cx - size * 0.18), (int) mastBottomY);
            base.addPoint((int) (cx + size * 0.18), (int) mastBottomY);
            g2.drawPolygon(base);

            // Animated pulse arc inside the fan.
            double pulseRadius = radius * (0.35 + 0.45 * ((frameCount % 45) / 45.0));
            Arc2D.Double pulse = new Arc2D.Double(
                    cx - pulseRadius,
                    cy - pulseRadius,
                    2.0 * pulseRadius,
                    2.0 * pulseRadius,
                    start,
                    sweep,
                    Arc2D.OPEN);

            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(pulse);
        }
    }

    /**
     * Painter for ship-based 360-degree surveillance radars.
     */
    private static final class ShipRadarPainter implements ButtonPainter {

        @Override
        public Dimension getPreferredSize() {
            return PREFERRED_SIZE;
        }

        @Override
        public void draw(Graphics2D g2, AbstractButton button,
                         Rectangle bounds, long frameCount) {

            prepare(g2);

            int size = Math.min(bounds.width, bounds.height);
            int pad = Math.max(3, size / 10);

            double cx = bounds.getCenterX();
            double cy = bounds.getCenterY() - size * 0.08;
            double radius = size * 0.34;

            boolean selected = button.isSelected();
            boolean rollover = button.getModel().isRollover();
            boolean pressed = button.getModel().isPressed();

            Color outline = stateColor(button, selected, rollover, pressed);
            Color sweepColor = new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 80);

            // Ship hull.
            double hullY = bounds.y + bounds.height - pad - size * 0.18;

            Polygon hull = new Polygon();
            hull.addPoint(bounds.x + pad + 2, (int) hullY);
            hull.addPoint(bounds.x + bounds.width - pad - 2, (int) hullY);
            hull.addPoint(bounds.x + bounds.width - pad - size / 5, bounds.y + bounds.height - pad);
            hull.addPoint(bounds.x + pad + size / 5, bounds.y + bounds.height - pad);

            g2.setColor(new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 35));
            g2.fillPolygon(hull);

            g2.setColor(outline);
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolygon(hull);

            // Superstructure / mast.
            g2.draw(new Line2D.Double(cx, hullY, cx, cy + radius * 0.7));

            Polygon superstructure = new Polygon();
            superstructure.addPoint((int) (cx - size * 0.16), (int) hullY);
            superstructure.addPoint((int) (cx - size * 0.07), (int) (hullY - size * 0.22));
            superstructure.addPoint((int) (cx + size * 0.11), (int) (hullY - size * 0.22));
            superstructure.addPoint((int) (cx + size * 0.18), (int) hullY);
            g2.drawPolygon(superstructure);

            // Radar range circle.
            Ellipse2D.Double circle = new Ellipse2D.Double(
                    cx - radius,
                    cy - radius,
                    2.0 * radius,
                    2.0 * radius);

            g2.setColor(new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 25));
            g2.fill(circle);

            g2.setColor(outline);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(circle);

            // Rotating sweep.
            double angle = frameCount * 0.13;
            double x2 = cx + radius * Math.cos(angle);
            double y2 = cy + radius * Math.sin(angle);

            g2.setColor(sweepColor);
            g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(cx, cy, x2, y2));

            // Center dot.
            double dot = Math.max(3.0, size * 0.10);
            g2.setColor(outline);
            g2.fill(new Ellipse2D.Double(cx - dot / 2.0, cy - dot / 2.0, dot, dot));
        }
    }

    private static void prepare(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
    }

    private static Color stateColor(AbstractButton button,
                                    boolean selected,
                                    boolean rollover,
                                    boolean pressed) {
        if (!button.isEnabled()) {
            return new Color(120, 120, 120);
        }

        if (pressed) {
            return new Color(255, 210, 80);
        }

        if (selected) {
            return new Color(255, 170, 40);
        }

        if (rollover) {
            return new Color(90, 190, 255);
        }

        return new Color(60, 160, 220);
    }
    

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!isSelected()) {
            return;
        }

        boolean placed = view.tryPlaceRadar(parameters, e.getPoint());
        if (placed) {
            toolBar.resetDefaultToggleButton();
        }
    }

    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}