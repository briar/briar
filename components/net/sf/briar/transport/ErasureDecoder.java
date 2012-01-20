package net.sf.briar.transport;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.Segment;

interface ErasureDecoder {

	/**
	 * Decodes the given set of segments into the given frame, or returns false
	 * if the segments cannot be decoded. The segment set may contain nulls.
	 */
	public boolean decodeFrame(Frame f, Segment[] set) throws FormatException;
}
