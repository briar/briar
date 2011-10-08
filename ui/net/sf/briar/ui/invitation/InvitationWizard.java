package net.sf.briar.ui.invitation;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.Wizard;

class InvitationWizard extends Wizard {

	private static final int WIDTH = 400, HEIGHT = 300;

	InvitationWizard(I18n i18n) {
		super(i18n, new Stri18ng("INVITATION_TITLE", i18n), WIDTH, HEIGHT);
	}

	@Override
	public void display() {
		showPanel("Intro");
		super.display();
	}
}
