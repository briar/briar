package org.briarproject.bramble.keyagreement;

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
	PayloadEncoder providePayloadEncoder(PayloadEncoderImpl payloadEncoder) {
		return payloadEncoder;
	}

	@Provides
	PayloadParser providePayloadParser(PayloadParserImpl payloadParser) {
		return payloadParser;
	}

	@Provides
	ConnectionChooser provideConnectionChooser(
			ConnectionChooserImpl connectionChooser) {
		return connectionChooser;
	}
}
