package edu.cnu.mdi.radar.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import edu.cnu.mdi.component.LabeledButtonContainer;

/**
 * A compact palette of radar buttons.
 *
 * <p>Each {@link RadarButton} supplies its own
 * {@link LabeledButtonContainer}. The palette arranges those labeled
 * containers in a fixed-column grid, but unlike {@code GridLayout}, this
 * implementation keeps the controls packed near the top instead of stretching
 * rows to fill all available vertical space.</p>
 */
@SuppressWarnings("serial")
public class RadarButtonPalette extends JPanel {

    private static final int DEFAULT_HGAP = 6;
    private static final int DEFAULT_VGAP = 6;

    private final int numColumns;
    private final List<RadarButton> buttons;
 
    /**
     * Creates a radar button palette.
     *
     * @param numColumns number of columns in the palette; must be positive
     * @param buttons radar buttons to include; must not be {@code null}
     */
    public RadarButtonPalette(int numColumns, List<RadarButton> buttons) {
        this(numColumns, buttons, DEFAULT_HGAP, DEFAULT_VGAP);
    }

    /**
     * Creates a radar button palette.
     *
     * @param numColumns number of columns in the palette; must be positive
     * @param buttons radar buttons to include; must not be {@code null}
     * @param hgap horizontal gap between cells
     * @param vgap vertical gap between rows
     */
    public RadarButtonPalette(int numColumns, List<RadarButton> buttons,
                              int hgap, int vgap) {
        if (numColumns <= 0) {
            throw new IllegalArgumentException("numColumns must be positive: " + numColumns);
        }

        Objects.requireNonNull(buttons, "buttons");

        this.numColumns = numColumns;
        this.buttons = Collections.unmodifiableList(new ArrayList<>(buttons));

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        setLayout(new GridBagLayout());

        build(hgap, vgap);

        // Helpful when this palette lives inside a BoxLayout parent.
        Dimension pref = getPreferredSize();
        setMaximumSize(pref);
    }
    
    /**
     * Recomputes the preferred and maximum size after layout-affecting changes,
     * such as adding a titled border.
     */
    public void packPaletteSize() {
        revalidate();

        Dimension pref = getPreferredSize();
        setPreferredSize(pref);
        setMaximumSize(pref);

        revalidate();
        repaint();
    }

    private void build(int hgap, int vgap) {
        removeAll();

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        gbc.weighty = 0.0;
        gbc.insets = new Insets(0, 0, vgap, hgap);

        int index = 0;
        for (RadarButton button : buttons) {
            if (button == null) {
                continue;
            }

            LabeledButtonContainer container = button.getLabeledButtonContainer();
            container.setAlignmentX(Component.LEFT_ALIGNMENT);

            int row = index / numColumns;
            int col = index % numColumns;

            gbc.gridx = col;
            gbc.gridy = row;

            // No trailing horizontal gap on the last column.
            gbc.insets = new Insets(0, 0, vgap, (col == numColumns - 1) ? 0 : hgap);

            add(container, gbc);
            index++;
        }

        // Filler row to absorb all extra vertical space and keep the palette
        // packed tightly at the top.
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = Math.max(1, (index + numColumns - 1) / numColumns);
        filler.gridwidth = numColumns;
        filler.weightx = 1.0;
        filler.weighty = 1.0;
        filler.fill = GridBagConstraints.BOTH;
        add(new JPanel(), filler);

        revalidate();
        repaint();
    }

    /**
     * Returns the immutable list of buttons in this palette.
     *
     * @return radar buttons
     */
    public List<RadarButton> getButtons() {
        return buttons;
    }

    /**
     * Returns the currently selected radar button, or {@code null} if none is
     * selected.
     *
     * @return selected radar button, or {@code null}
     */
    public RadarButton getSelectedRadarButton() {
        for (RadarButton button : buttons) {
            if ((button != null) && button.isSelected()) {
                return button;
            }
        }
        return null;
    }

 
    /**
     * Returns the number of columns requested for this palette.
     *
     * @return number of columns
     */
    public int getNumColumns() {
        return numColumns;
    }
}