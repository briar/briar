package net.sf.briar.plugins.modem;

interface ReliabilityLayerFactory {

	ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler);
}
