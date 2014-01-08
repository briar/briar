package org.briarproject.api.reliability;

public interface ReliabilityLayerFactory {

	/** Returns a reliability layer that writes to the given lower layer. */
	ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler);
}
