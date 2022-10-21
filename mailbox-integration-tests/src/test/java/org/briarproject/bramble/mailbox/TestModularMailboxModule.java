package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.mailbox.AbstractMailboxIntegrationTest.URL_BASE;

@Module
@NotNullByDefault
class TestModularMailboxModule {

	@Provides
	MailboxConfig provideMailboxConfig(TestMailboxConfigImpl mailboxConfig) {
		return mailboxConfig;
	}

	static UrlConverter urlConverter = onion -> URL_BASE;

	@Provides
	UrlConverter provideUrlConverter() {
		return urlConverter;
	}
}
