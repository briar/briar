package net.sf.briar.api.plugins;

import java.io.IOException;

import net.sf.briar.api.transport.Segment;

public interface SegmentSource {

	/**
	 * Attempts to read a segment into the given buffer and returns true if a
	 * segment was read, or false if no more segments can be read.
	 */
	boolean readSegment(Segment s) throws IOException;

	/**
	 * Returns the maximum length in bytes of the segments this source returns.
	 */
	int getMaxSegmentLength();
}
