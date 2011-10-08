package net.sf.briar.ui.setup;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.Wizard;

class SetupWizard extends Wizard {

	private static int WIDTH = 400, HEIGHT = 300;

	SetupWizard(I18n i18n) {
		super(i18n, new Stri18ng("SETUP_TITLE", i18n), WIDTH, HEIGHT);
	}

	@Override
	public void display() {
		showPanel("Language");
		super.display();
	}
}