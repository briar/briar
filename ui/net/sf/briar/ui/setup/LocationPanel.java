package net.sf.briar.ui.setup;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;
import net.sf.briar.ui.wizard.DirectoryChooserPanel;

public class LocationPanel extends DirectoryChooserPanel {

	private static final long serialVersionUID = -8831098591612528860L;

	LocationPanel(SetupWizard wizard, I18n i18n) {
		super(wizard, "Location", "AlreadyInstalled", "SetupWorker",
				new Stri18ng("SETUP_LOCATION_TITLE", i18n),
				new Stri18ng("SETUP_LOCATION_TEXT", i18n), i18n);
	}
}
