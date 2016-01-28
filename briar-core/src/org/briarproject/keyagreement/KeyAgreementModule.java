package org.briarproject.keyagreement;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.event.EventBus;
import org.briarproject.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.api.keyagreement.PayloadEncoder;
import org.briarproject.api.keyagreement.PayloadParser;
import org.briarproject.api.lifecycle.IoExecutor;
import org.briarproject.api.plugins.PluginManager;
import org.briarproject.api.system.Clock;

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
