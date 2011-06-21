package net.sf.briar.ui.setup;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.TextPanel;

class InstructionsPanel extends TextPanel {

	private static final long serialVersionUID = -8730283083962607067L;

	InstructionsPanel(SetupWizard wizard, I18n i18n) {
		super(wizard, "Instructions", new Stri18ng("SETUP_INSTRUCTIONS", i18n));
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(true);
		wizard.setNextButtonEnabled(false);
		wizard.setFinished(true);
	}

	@Override
	protected void backButtonPressed() {
		wizard.showPanel("AlreadyInstalled");
	}

	@Override
	protected void nextButtonPressed() {
		assert false;
	}

	@Override
	protected void cancelButtonPressed() {
		assert false;
	}

	@Override
	protected void finishButtonPressed() {
		System.exit(0);
	}
}
