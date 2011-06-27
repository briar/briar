package net.sf.briar.api.setup;

import java.io.File;

/** Provides the parameters for the installation process. */
public interface SetupParameters {

	File getChosenLocation();

	long getExeHeaderSize();
}
