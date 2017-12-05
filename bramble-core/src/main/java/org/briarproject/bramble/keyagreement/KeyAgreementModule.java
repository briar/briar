package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.data.BdfWriterFactory;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.PayloadParser;

import dagger.Module;
import dagger.Provides;

@Module
public class KeyAgreementModule {

	@Provides
	KeyAgreementTask provideKeyAgreementTask(
			KeyAgreementTaskImpl keyAgreementTask) {
		return keyAgreementTask;
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
