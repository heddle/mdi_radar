package edu.cnu.mdi.radar.app;

import java.awt.Color;
import java.awt.EventQueue;

import edu.cnu.mdi.app.BaseMDIApplication;
import edu.cnu.mdi.radar.ui.RadarView;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.LogView;
import edu.cnu.mdi.view.ViewManager;

@SuppressWarnings("serial")
public class RadarApp extends BaseMDIApplication {

	/**
	 * Constructor.
	 *
	 * @param keyVals key-value pairs for configuring the base MDI application
	 */
	public RadarApp(Object... keyVals) {
		super(keyVals);
	}

	/**
	 * Create and register the initial set of views shown in the demo.
	 * <p>
	 * This method only builds views; it should not depend on the outer frame being
	 * shown or on final geometry.
	 */
	@Override
	protected void addInitialViews() {
		LogView logView = new LogView();
		ViewManager.getInstance().getViewMenu().addSeparator();
		logView.setVisible(false);
		
		RadarView radarView = new RadarView();
	}
	
	@Override
	protected String getApplicationId() {
		return "Radar Visualization Demo";
	}

	/** Main entry point. */
	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			RadarApp app = new RadarApp(PropertyUtils.TITLE, "Radar Visualization Demo",
					PropertyUtils.FRACTION, 0.8,
					PropertyUtils.BACKGROUND, Color.darkGray);
			app.setVisible(true);
		});
	}
	
}
