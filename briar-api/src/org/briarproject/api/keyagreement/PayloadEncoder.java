package org.briarproject.api.keyagreement;

public interface PayloadEncoder {

	byte[] encode(Payload p);
}
