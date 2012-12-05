package net.sf.briar.db;

import java.io.IOException;

import net.sf.briar.TestUtils;
import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.GroupFactory;
import net.sf.briar.api.protocol.GroupId;

class TestGroupFactory implements GroupFactory {

	public Group createGroup(String name, byte[] publicKey) throws IOException {
		GroupId id = new GroupId(TestUtils.getRandomId());
		return new TestGroup(id, name, publicKey);
	}

	public Group createGroup(GroupId id, String name, byte[] publicKey) {
		return new TestGroup(id, name, publicKey);
	}
}