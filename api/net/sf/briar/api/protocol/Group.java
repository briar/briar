package net.sf.briar.api.protocol;

import net.sf.briar.api.serial.Writable;

/** A group to which users may subscribe. */
public interface Group extends Writable {

	/** Returns the group's unique identifier. */
	GroupId getId();

	/** Returns the group's name. */
	String getName();

	/**
	 * If the group is restricted, returns the public key that is used to
	 * authorise all messages sent to the group. Otherwise returns null.
	 */
	byte[] getPublicKey();
}
