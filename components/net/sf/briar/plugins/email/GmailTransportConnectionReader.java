package net.sf.briar.plugins.email;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.microedition.io.StreamConnection;

import net.sf.briar.api.plugins.simplex.SimplexTransportReader;

public class GmailTransportConnectionReader implements SimplexTransportReader{

	private static final Logger LOG = Logger.getLogger(GmailTransportConnectionReader.class.getName());

	private final StreamConnection stream;

	GmailTransportConnectionReader(StreamConnection stream) {
			this.stream = stream;
	}
	
	public InputStream getInputStream() throws IOException {
		
			return stream.openInputStream();
	}

	public void dispose(boolean exception, boolean recognised) {
		try {
			stream.close();
		} catch(IOException e) {
			if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.toString());
		}		
	}

}
