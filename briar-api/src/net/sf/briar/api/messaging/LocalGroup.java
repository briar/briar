package net.sf.briar.api.messaging;

/** A restricted group to which the user can post messages. */
public class LocalGroup extends Group {

	private final byte[] privateKey;

	public LocalGroup(GroupId id, String name, byte[] publicKey,
			byte[] privateKey) {
		super(id, name, publicKey);
		this.privateKey = privateKey;
	}

	/** Returns the private key that is used to sign messages. */
	public byte[] getPrivateKey() {
		return privateKey;
	}
}
