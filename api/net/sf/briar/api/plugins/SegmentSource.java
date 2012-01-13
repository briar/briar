package net.sf.briar.api.plugins;

import java.io.IOException;

public interface SegmentSource {

	/**
	 * Attempts to read a segment into the given buffer and returns true if a
	 * segment was read, or false if no more segments can be read.
	 */
	boolean readSegment(Segment s) throws IOException;
}
