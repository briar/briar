package net.sf.briar.plugins.file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.transport.batch.BatchTransportReader;

class FileTransportReader implements BatchTransportReader {

	private final File file;
	private final InputStream in;

	private boolean streamInUse = false;

	FileTransportReader(File file, InputStream in) {
		this.file = file;
		this.in = in;
	}

	public InputStream getInputStream() {
		streamInUse = true;
		return in;
	}

	public void dispose() throws IOException {
		if(streamInUse) in.close();
		file.delete();
	}
}
