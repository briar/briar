package org.briarproject.bramble.reporting;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.reporting.DevConfig;
import org.briarproject.bramble.api.reporting.DevReporter;

import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;

@Module
public class ReportingModule {

	@Provides
	DevReporter provideDevReporter(CryptoComponent crypto,
			DevConfig devConfig, SocketFactory torSocketFactory) {
		return new DevReporterImpl(crypto, devConfig, torSocketFactory);
	}
}
