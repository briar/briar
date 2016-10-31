package org.briarproject.transport;

import org.briarproject.api.TransportId;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DatabaseExecutor;
import org.briarproject.api.system.Clock;
import org.briarproject.api.transport.TransportKeyManager;
import org.briarproject.api.transport.TransportKeyManagerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;

public class TransportKeyManagerFactoryImpl implements
		TransportKeyManagerFactory {

	private final DatabaseComponent db;
	private final CryptoComponent crypto;
	private final Executor dbExecutor;
	private final ScheduledExecutorService scheduler;
	private final Clock clock;

	@Inject
	TransportKeyManagerFactoryImpl(DatabaseComponent db, CryptoComponent crypto,
			@DatabaseExecutor Executor dbExecutor,
			ScheduledExecutorService scheduler, Clock clock) {
		this.db = db;
		this.crypto = crypto;
		this.dbExecutor = dbExecutor;
		this.scheduler = scheduler;
		this.clock = clock;
	}

	@Override
	public TransportKeyManager createTransportKeyManager(
			TransportId transportId, long maxLatency) {
		return new TransportKeyManagerImpl(db, crypto, dbExecutor, scheduler,
				clock, transportId, maxLatency);
	}

}
