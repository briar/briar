package org.briarproject.bramble.api.reliability;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface ReliabilityLayerFactory {

	/**
	 * Returns a reliability layer that writes to the given lower layer.
	 */
	ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler);
}
