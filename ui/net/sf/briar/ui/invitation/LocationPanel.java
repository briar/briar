package net.sf.briar.ui.invitation;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.DirectoryChooserPanel;
import net.sf.briar.ui.wizard.Wizard;

import com.google.inject.Inject;

class LocationPanel extends DirectoryChooserPanel {

	private static final long serialVersionUID = 3788640725729516888L;

	@Inject
	LocationPanel(Wizard wizard, I18n i18n) {
		super(wizard, "Location", "Password", "InvitationWorker",
				new Stri18ng("INVITATION_LOCATION_TITLE", i18n),
				new Stri18ng("INVITATION_LOCATION_TEXT", i18n), i18n);
	}
}
