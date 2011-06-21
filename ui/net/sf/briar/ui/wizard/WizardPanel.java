package net.sf.briar.ui.wizard;

import javax.swing.JPanel;

import net.sf.briar.api.i18n.I18n;

public abstract class WizardPanel extends JPanel implements I18n.Listener {

	private static final long serialVersionUID = 8657047449339969485L;

	protected final Wizard wizard;

	protected WizardPanel(Wizard wizard, String id) {
		this.wizard = wizard;
		wizard.registerPanel(id, this);
	}

	protected abstract void display();

	protected abstract void backButtonPressed();

	protected abstract void nextButtonPressed();

	protected abstract void cancelButtonPressed();

	protected abstract void finishButtonPressed();
}
