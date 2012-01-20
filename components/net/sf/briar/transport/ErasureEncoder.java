package net.sf.briar.transport;

import net.sf.briar.api.transport.Segment;

interface ErasureEncoder {

	/** Encodes the given frame as a set of segments. */
	Segment[] encodeFrame(Frame f);
}
