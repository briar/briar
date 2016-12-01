package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.system.Clock;

import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
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

	@Override
	public KeyAgreementTask createTask() {
		return new KeyAgreementTaskImpl(clock, crypto, eventBus, payloadEncoder,
				pluginManager, ioExecutor);
	}
}
