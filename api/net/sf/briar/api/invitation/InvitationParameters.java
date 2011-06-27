package net.sf.briar.api.invitation;

import java.io.File;

/** Provides the parameters for creating an invitation. */
public interface InvitationParameters {

	boolean shouldCreateExe();

	boolean shouldCreateJar();

	char[] getPassword();

	File getChosenLocation();

	File getSetupDat();
}
