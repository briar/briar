package net.sf.briar.api.protocol.writers;

import java.io.IOException;
import java.util.BitSet;

import net.sf.briar.api.protocol.OfferId;

/** An interface for creating a request packet. */
public interface RequestWriter {

	/** Writes the contents of the request. */
	void writeRequest(OfferId offerId, BitSet b, int length) throws IOException;
}
