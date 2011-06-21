package net.sf.briar.api.invitation;

import java.io.File;

public interface InvitationParameters {

	boolean shouldCreateExe();

	boolean shouldCreateJar();

	char[] getPassword();

	File getChosenLocation();

	String[] getBundledFontFilenames();
}
