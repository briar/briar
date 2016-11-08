package org.briarproject.sync;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.util.StringUtils;

import javax.inject.Inject;

class GroupFactoryImpl implements GroupFactory {

	private final CryptoComponent crypto;

	@Inject
	GroupFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	public Group createGroup(ClientId c, byte[] descriptor) {
		byte[] hash =
				crypto.hash(GroupId.LABEL, StringUtils.toUtf8(c.getString()),
						descriptor);
		return new Group(new GroupId(hash), c, descriptor);
	}
}
