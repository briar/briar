package net.sf.briar.ui.invitation;

import java.io.File;

import net.sf.briar.api.invitation.InvitationParameters;
import net.sf.briar.util.FileUtils;

class InvitationParametersImpl implements InvitationParameters {

	private final ExistingUserPanel existingUserPanel;
	private final OperatingSystemPanel osPanel;
	private final PasswordPanel passwordPanel;
	private final LocationPanel locationPanel;

	InvitationParametersImpl(ExistingUserPanel existingUserPanel,
			OperatingSystemPanel osPanel, PasswordPanel passwordPanel,
			LocationPanel locationPanel) {
		this.existingUserPanel = existingUserPanel;
		this.osPanel = osPanel;
		this.passwordPanel = passwordPanel;
		this.locationPanel = locationPanel;
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

	public File getSetupDat() {
		return FileUtils.getBriarDirectory();
	}
}
