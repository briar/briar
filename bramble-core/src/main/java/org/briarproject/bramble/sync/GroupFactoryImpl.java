package org.briarproject.bramble.sync;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.util.StringUtils;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class GroupFactoryImpl implements GroupFactory {

	private final CryptoComponent crypto;

	@Inject
	GroupFactoryImpl(CryptoComponent crypto) {
		this.crypto = crypto;
	}

	@Override
	public Group createGroup(ClientId c, byte[] descriptor) {
		byte[] hash = crypto.hash(GroupId.LABEL,
				StringUtils.toUtf8(c.getString()), descriptor);
		return new Group(new GroupId(hash), c, descriptor);
	}
}
