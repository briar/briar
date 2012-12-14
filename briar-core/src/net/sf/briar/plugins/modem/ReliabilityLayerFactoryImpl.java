package net.sf.briar.plugins.modem;

import java.util.concurrent.Executor;

class ReliabilityLayerFactoryImpl implements ReliabilityLayerFactory {

	private final Executor executor;

	ReliabilityLayerFactoryImpl(Executor executor) {
		this.executor = executor;
	}

	public ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler) {
		return new ReliabilityLayerImpl(executor, writeHandler);
	}
}
