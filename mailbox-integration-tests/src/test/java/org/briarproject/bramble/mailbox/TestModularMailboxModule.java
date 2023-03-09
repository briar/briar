package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import java.util.function.IntSupplier;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.bramble.mailbox.AbstractMailboxIntegrationTest.URL_BASE;

@Module
@NotNullByDefault
class TestModularMailboxModule {

	private final IntSupplier portSupplier;

	TestModularMailboxModule(IntSupplier portSupplier) {
		this.portSupplier = portSupplier;
	}

	@Provides
	MailboxConfig provideMailboxConfig(TestMailboxConfigImpl mailboxConfig) {
		return mailboxConfig;
	}

	@Provides
	UrlConverter provideUrlConverter() {
		return onion -> {
			int port = portSupplier.getAsInt();
			return URL_BASE + ":" + port;
		};
	}
}
