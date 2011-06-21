package net.sf.briar.ui.invitation;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.TextPanel;
import net.sf.briar.ui.wizard.Wizard;

class IntroPanel extends TextPanel {

	private static final long serialVersionUID = 2428034340183141779L;

	IntroPanel(Wizard wizard, I18n i18n) {
		super(wizard, "Intro", new Stri18ng("INVITATION_INTRO", i18n));
	}

	@Override
	protected void display() {
		wizard.setBackButtonEnabled(false);
		wizard.setNextButtonEnabled(true);
		wizard.setFinished(false);
	}

	@Override
	protected void backButtonPressed() {
		assert false;
	}

	@Override
	protected void nextButtonPressed() {
		wizard.showPanel("ExistingUser");
	}

	@Override
	protected void cancelButtonPressed() {
		wizard.close();
	}

	@Override
	protected void finishButtonPressed() {
		assert false;
	}
}
