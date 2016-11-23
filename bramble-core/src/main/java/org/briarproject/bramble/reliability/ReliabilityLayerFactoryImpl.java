package org.briarproject.bramble.reliability;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.reliability.ReliabilityLayer;
import org.briarproject.bramble.api.reliability.ReliabilityLayerFactory;
import org.briarproject.bramble.api.reliability.WriteHandler;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.system.SystemClock;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class ReliabilityLayerFactoryImpl implements ReliabilityLayerFactory {

	private final Executor ioExecutor;
	private final Clock clock;

	@Inject
	ReliabilityLayerFactoryImpl(@IoExecutor Executor ioExecutor) {
		this.ioExecutor = ioExecutor;
		clock = new SystemClock();
	}

	@Override
	public ReliabilityLayer createReliabilityLayer(WriteHandler writeHandler) {
		return new ReliabilityLayerImpl(ioExecutor, clock, writeHandler);
	}
}
