package net.sf.briar.plugins.file;

import java.io.File;

public class TestFilePlugin extends FilePlugin {

	private final File outputDir;
	private final long capacity;

	public TestFilePlugin(File outputDir, long capacity) {
		this.outputDir = outputDir;
		this.capacity = capacity;
	}

	@Override
	protected File chooseOutputDirectory() {
		return outputDir;
	}

	@Override
	protected void writerFinished(File f) {
		// Nothing to do
	}

	@Override
	protected long getCapacity(String path) {
		return capacity;
	}
}
