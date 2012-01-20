package net.sf.briar.api.plugins;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

public interface SegmentSink {

	/** Writes the given segment. */
	void writeSegment(Segment s) throws IOException;

	/**
	 * Returns the maximum length in bytes of the segments this sink accepts.
	 */
	int getMaxSegmentLength();
}
