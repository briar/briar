package org.briarproject.keyagreement;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.KeyAgreementAbortedEvent;
import org.briarproject.api.event.KeyAgreementFailedEvent;
import org.briarproject.api.event.KeyAgreementFinishedEvent;
import org.briarproject.api.keyagreement.KeyAgreementTask;
import org.briarproject.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.api.keyagreement.PayloadEncoder;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.system.Clock;

import java.util.concurrent.Executor;

import javax.inject.Inject;

class KeyAgreementTaskFactoryImpl implements KeyAgreementTaskFactory {

	private final Clock clock;
	private final CryptoComponent crypto;
	private final EventBus eventBus;
	private final Executor ioExecutor;
	private final PayloadEncoder payloadEncoder;
	private final PluginManager pluginManager;

	@Inject
	KeyAgreementTaskFactoryImpl(Clock clock, CryptoComponent crypto,
			EventBus eventBus, @IoExecutor Executor ioExecutor,
			PayloadEncoder payloadEncoder, PluginManager pluginManager) {
		this.clock = clock;
		this.crypto = crypto;
		this.eventBus = eventBus;
		this.ioExecutor = ioExecutor;
		this.payloadEncoder = payloadEncoder;
		this.pluginManager = pluginManager;
	}

	public KeyAgreementTask getTask() {
		return new KeyAgreementTaskImpl(clock, crypto, eventBus, payloadEncoder,
				pluginManager, ioExecutor);
	}
}
