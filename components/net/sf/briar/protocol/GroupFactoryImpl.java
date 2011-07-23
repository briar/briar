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

	public Group createGroup(GroupId id, String name, boolean restricted,
			byte[] saltOrKey) {
		if(restricted) {
			try {
				PublicKey key = keyParser.parsePublicKey(saltOrKey);
				return new GroupImpl(id, name, key);
			} catch(InvalidKeySpecException e) {
				throw new IllegalArgumentException(e);
			}
		} else return new GroupImpl(id, name, saltOrKey);
	}
}
