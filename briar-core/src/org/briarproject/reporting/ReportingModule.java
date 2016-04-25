package org.briarproject.reporting;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.reporting.DevConfig;
import org.briarproject.api.reporting.DevReporter;

import dagger.Module;
import dagger.Provides;

@Module
public class ReportingModule {

	@Provides
	DevReporter provideDevReportTask(CryptoComponent crypto,
			DevConfig devConfig) {
		return new DevReporterImpl(crypto, devConfig);
	}
}
