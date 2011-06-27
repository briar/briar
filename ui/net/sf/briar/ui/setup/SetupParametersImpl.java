package net.sf.briar.ui.setup;

import java.io.File;

import net.sf.briar.api.setup.SetupParameters;

class SetupParametersImpl implements SetupParameters {

	private static final int EXE_HEADER_SIZE = 62976;

	private final LocationPanel locationPanel;

	SetupParametersImpl(LocationPanel locationPanel) {
		this.locationPanel = locationPanel;
	}

	public File getChosenLocation() {
		return locationPanel.getChosenDirectory();
	}

	public long getExeHeaderSize() {
		return EXE_HEADER_SIZE;
	}
}
