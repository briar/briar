package org.briarproject.api.keyagreement;

import org.briarproject.api.nullsafety.NotNullByDefault;

import java.io.IOException;

@NotNullByDefault
public interface PayloadParser {

	Payload parse(byte[] raw) throws IOException;
}
