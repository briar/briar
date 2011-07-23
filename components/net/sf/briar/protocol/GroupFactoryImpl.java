package net.sf.briar.protocol;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import net.sf.briar.api.crypto.KeyParser;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;

import com.google.inject.Inject;

class GroupFactoryImpl implements GroupFactory {

	private final KeyParser keyParser;

	@Inject
	GroupFactoryImpl(KeyParser keyParser) {
		this.keyParser = keyParser;
	}

	public Group createGroup(GroupId id, String name, byte[] salt,
			byte[] publicKey) {
		if(salt == null && publicKey == null)
			throw new IllegalArgumentException();
		if(salt != null && publicKey != null)
			throw new IllegalArgumentException();
		PublicKey key = null;
		if(publicKey != null) {
			try {
				key = keyParser.parsePublicKey(publicKey);
			} catch (InvalidKeySpecException e) {
				throw new IllegalArgumentException(e);
			}
		}
		return new GroupImpl(id, name, salt, key);
	}
}
