package org.briarproject.bramble.mailbox;

import dagger.Module;
import dagger.Provides;

@Module
public class UrlConverterModule {

	@Provides
	UrlConverter provideUrlConverter(UrlConverterImpl urlConverter) {
		return urlConverter;
	}
}
