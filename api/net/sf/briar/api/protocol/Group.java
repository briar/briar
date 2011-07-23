package net.sf.briar.api.protocol;

import java.security.PublicKey;

import net.sf.briar.api.serial.Writable;

/** A group to which users may subscribe. */
public interface Group extends Writable {

	/** Returns the group's unique identifier. */
	GroupId getId();

	/** Returns the group's name. */
	String getName();

	/**
	 * Returns true if messages sent to the group must be signed with a
	 * particular private key.
	 */
	boolean isRestricted();

	/**
	 * If the group is restricted, returns null. Otherwise returns a salt
	 * value that is combined with the group's name to generate its unique
	 * identifier.
	 */
	byte[] getSalt();

	/**
	 * If the group is restricted, returns the public key that is used to
	 * authorise all messages sent to the group. Otherwise returns null.
	 */
	PublicKey getPublicKey();
}
