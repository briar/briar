package org.briarproject.bramble.mailbox;

import org.briarproject.nullsafety.NotNullByDefault;

import static org.briarproject.bramble.mailbox.MailboxIntegrationTestUtils.URL_BASE;

@NotNullByDefault
class TestUrlConverter implements UrlConverter {

	@Override
	public String convertOnionToBaseUrl(String onion) {
		return URL_BASE;
	}
}
