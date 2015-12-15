package org.briarproject.api.sync;

/** Packet types for the messaging protocol. */
public interface PacketTypes {

	byte ACK = 0;
	byte MESSAGE = 1;
	byte OFFER = 2;
	byte REQUEST = 3;
	byte RETENTION_ACK = 4;
	byte RETENTION_UPDATE = 5;
	byte SUBSCRIPTION_ACK = 6;
	byte SUBSCRIPTION_UPDATE = 7;
	byte TRANSPORT_ACK = 8;
	byte TRANSPORT_UPDATE = 9;
}
