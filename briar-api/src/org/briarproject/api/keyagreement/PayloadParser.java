package org.briarproject.api.keyagreement;

import java.io.IOException;

public interface PayloadParser {

	Payload parse(byte[] raw) throws IOException;
}
