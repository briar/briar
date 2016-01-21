package org.briarproject.api.sync;

/** Packet types for the sync protocol. */
public interface PacketTypes {

	byte ACK = 0;
	byte MESSAGE = 1;
	byte OFFER = 2;
	byte REQUEST = 3;
	byte SUBSCRIPTION_ACK = 6;
	byte SUBSCRIPTION_UPDATE = 7;
}
