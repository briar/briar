package org.briarproject.bramble.api.keyagreement;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface PayloadParser {

	Payload parse(byte[] raw) throws IOException;
}
