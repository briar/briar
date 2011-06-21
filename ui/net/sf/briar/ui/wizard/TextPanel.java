package net.sf.briar.ui.wizard;

import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.SwingConstants;

import net.sf.briar.api.i18n.Stri18ng;

public abstract class TextPanel extends WizardPanel {

	private static final long serialVersionUID = -3046102503813671049L;

	private final Stri18ng text;
	private final JLabel label;

	protected TextPanel(Wizard wizard, String id, Stri18ng text) {
		super(wizard, id);
		this.text = text;
		label = new JLabel(text.html());
		Dimension d = wizard.getPreferredSize();
		label.setPreferredSize(new Dimension(d.width - 50, d.height - 80));
		label.setVerticalAlignment(SwingConstants.TOP);
		add(label);
	}

	public void localeChanged(Font uiFont) {
		label.setText(text.html());
		label.setFont(uiFont);
	}
}
