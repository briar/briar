package org.briarproject.bramble.api.sync;

/**
 * Record types for the sync protocol.
 */
public interface RecordTypes {

	byte ACK = 0;
	byte MESSAGE = 1;
	byte OFFER = 2;
	byte REQUEST = 3;

}
