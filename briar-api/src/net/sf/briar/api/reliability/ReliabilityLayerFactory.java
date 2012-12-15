package net.sf.briar.api.reliability;

public interface ReliabilityLayerFactory {

	ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler);
}
