package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.BitSet;

/** An interface for creating a request packet. */
public interface RequestWriter {

	/** Writes the contents of the request. */
	void writeRequest(BitSet b, int length) throws IOException;
}
