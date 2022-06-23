package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.inject.Inject;

@NotNullByDefault
class UrlConverterImpl implements UrlConverter {

	@Inject
	UrlConverterImpl() {
	}

	@Override
	public String convertOnionToBaseUrl(String onion) {
		return "http://" + onion + ".onion";
	}
}
