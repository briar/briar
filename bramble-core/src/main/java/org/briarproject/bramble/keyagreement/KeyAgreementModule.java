package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.PayloadParser;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.system.Clock;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class KeyAgreementModule {

	@Provides
	@Singleton
	KeyAgreementTaskFactory provideKeyAgreementTaskFactory(Clock clock,
			CryptoComponent crypto, EventBus eventBus,
			@IoExecutor Executor ioExecutor, PayloadEncoder payloadEncoder,
			PluginManager pluginManager) {
		return new KeyAgreementTaskFactoryImpl(clock, crypto, eventBus,
				ioExecutor, payloadEncoder, pluginManager);
	}

	@Provides
	PayloadEncoder providePayloadEncoder(BdfWriterFactory bdfWriterFactory) {
		return new PayloadEncoderImpl(bdfWriterFactory);
	}

	@Provides
	PayloadParser providePayloadParser(BdfReaderFactory bdfReaderFactory) {
		return new PayloadParserImpl(bdfReaderFactory);
	}
}
