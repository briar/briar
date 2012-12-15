package net.sf.briar.reliability;

import java.util.concurrent.Executor;

import net.sf.briar.api.reliability.ReliabilityLayer;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;
import net.sf.briar.api.reliability.WriteHandler;

class ReliabilityLayerFactoryImpl implements ReliabilityLayerFactory {

	private final Executor executor;

	ReliabilityLayerFactoryImpl(Executor executor) {
		this.executor = executor;
	}

	public ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler) {
		return new ReliabilityLayerImpl(executor, writeHandler);
	}
}
