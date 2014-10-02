package org.briarproject.reliability;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.reliability.ReliabilityLayer;
import org.briarproject.api.reliability.ReliabilityLayerFactory;
import org.briarproject.api.reliability.WriteHandler;
import org.briarproject.api.system.Clock;
import org.briarproject.system.SystemClock;

class ReliabilityLayerFactoryImpl implements ReliabilityLayerFactory {

	private final Executor ioExecutor;
	private final Clock clock;

	@Inject
	ReliabilityLayerFactoryImpl(@IoExecutor Executor ioExecutor) {
		this.ioExecutor = ioExecutor;
		clock = new SystemClock();
	}

	public ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler) {
		return new ReliabilityLayerImpl(ioExecutor, clock, writeHandler);
	}
}
