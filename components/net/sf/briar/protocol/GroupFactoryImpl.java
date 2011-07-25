package net.sf.briar.protocol;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;

class GroupFactoryImpl implements GroupFactory {

	public Group createGroup(GroupId id, String name, byte[] publicKey) {
		return new GroupImpl(id, name, publicKey);
	}
}
