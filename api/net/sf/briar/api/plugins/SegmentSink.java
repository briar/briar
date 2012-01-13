package net.sf.briar.api.plugins;

import java.io.IOException;

public interface SegmentSink {

	/** Writes the given segment. */
	void writeSegment(Segment s) throws IOException;
}
