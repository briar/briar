package net.sf.briar.plugins.email;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.activation.DataSource;

public class PipeDataSource implements DataSource{

	public String getContentType() {
		return "application/octet-stream";
	}

	public PipedInputStream getInputStream() throws IOException {
		return null;
	}

	public String getName() {
		return "foo";
	}

	public PipedOutputStream getOutputStream() throws UnsupportedOperationException {
		return null;
	}

	

}
