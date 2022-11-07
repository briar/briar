package org.briarproject.bramble.mailbox;

import dagger.Module;
import dagger.Provides;

@Module
public class ModularMailboxModule {
	@Provides
	MailboxConfig provideMailboxConfig(MailboxConfigImpl mailboxConfig) {
		return mailboxConfig;
	}

	@Provides
	UrlConverter provideUrlConverter(UrlConverterImpl urlConverter) {
		return urlConverter;
	}
}
