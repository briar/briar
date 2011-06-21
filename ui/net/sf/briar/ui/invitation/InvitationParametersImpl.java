package net.sf.briar.ui.invitation;

import java.io.File;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.invitation.InvitationParameters;

import com.google.inject.Inject;

class InvitationParametersImpl implements InvitationParameters {

	private final ExistingUserPanel existingUserPanel;
	private final OperatingSystemPanel osPanel;
	private final PasswordPanel passwordPanel;
	private final LocationPanel locationPanel;
	private final FontManager fontManager;

	@Inject
	InvitationParametersImpl(ExistingUserPanel existingUserPanel,
			OperatingSystemPanel osPanel, PasswordPanel passwordPanel,
			LocationPanel locationPanel, FontManager fontManager) {
		this.existingUserPanel = existingUserPanel;
		this.osPanel = osPanel;
		this.passwordPanel = passwordPanel;
		this.locationPanel = locationPanel;
		this.fontManager = fontManager;
	}

	public boolean shouldCreateExe() {
		return existingUserPanel.shouldCreateInstaller()
		&& osPanel.shouldCreateExe();
	}

	public boolean shouldCreateJar() {
		return existingUserPanel.shouldCreateInstaller()
		&& osPanel.shouldCreateJar();
	}

	public char[] getPassword() {
		return passwordPanel.getPassword();
	}

	public File getChosenLocation() {
		return locationPanel.getChosenDirectory();
	}

	public String[] getBundledFontFilenames() {
		return fontManager.getBundledFontFilenames();
	}
}
