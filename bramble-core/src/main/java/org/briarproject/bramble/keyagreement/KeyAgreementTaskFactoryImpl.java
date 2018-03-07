package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class KeyAgreementTaskFactoryImpl implements KeyAgreementTaskFactory {

	private final CryptoComponent crypto;
	private final EventBus eventBus;
	private final PayloadEncoder payloadEncoder;
	private final PluginManager pluginManager;
	private final ConnectionChooser connectionChooser;

	@Inject
	KeyAgreementTaskFactoryImpl(CryptoComponent crypto, EventBus eventBus,
			PayloadEncoder payloadEncoder, PluginManager pluginManager,
			ConnectionChooser connectionChooser) {
		this.crypto = crypto;
		this.eventBus = eventBus;
		this.payloadEncoder = payloadEncoder;
		this.pluginManager = pluginManager;
		this.connectionChooser = connectionChooser;
	}

	@Override
	public KeyAgreementTask createTask() {
		return new KeyAgreementTaskImpl(crypto, eventBus, payloadEncoder,
				pluginManager, connectionChooser);
	}
}
