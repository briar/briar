package net.sf.briar.reliability;

import java.util.concurrent.Executor;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.reliability.ReliabilityLayer;
import net.sf.briar.api.reliability.ReliabilityLayerFactory;
import net.sf.briar.api.reliability.WriteHandler;

import com.google.inject.Inject;

class ReliabilityLayerFactoryImpl implements ReliabilityLayerFactory {

	private final Executor executor;
	private final Clock clock;

	@Inject
	ReliabilityLayerFactoryImpl(@ReliabilityExecutor Executor executor) {
		this.executor = executor;
		clock = new SystemClock();
	}

	public ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler) {
		return new ReliabilityLayerImpl(executor, clock, writeHandler);
	}
}
