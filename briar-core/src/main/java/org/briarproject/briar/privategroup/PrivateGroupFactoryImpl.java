package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;

import java.security.SecureRandom;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.GROUP_SALT_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;
import static org.briarproject.briar.api.privategroup.PrivateGroupManager.CLIENT_ID;

@Immutable
@NotNullByDefault
class PrivateGroupFactoryImpl implements PrivateGroupFactory {

	private final GroupFactory groupFactory;
	private final ClientHelper clientHelper;
	private final AuthorFactory authorFactory;
	private final SecureRandom random;

	@Inject
	PrivateGroupFactoryImpl(GroupFactory groupFactory,
			ClientHelper clientHelper, AuthorFactory authorFactory,
			SecureRandom random) {

		this.groupFactory = groupFactory;
		this.clientHelper = clientHelper;
		this.authorFactory = authorFactory;
		this.random = random;
	}

	@Override
	public PrivateGroup createPrivateGroup(String name, Author author) {
		int length = StringUtils.toUtf8(name).length;
		if (length == 0) throw new IllegalArgumentException("Group name empty");
		if (length > MAX_GROUP_NAME_LENGTH)
			throw new IllegalArgumentException(
					"Group name exceeds maximum length");
		byte[] salt = new byte[GROUP_SALT_LENGTH];
		random.nextBytes(salt);
		return createPrivateGroup(name, author, salt);
	}

	@Override
	public PrivateGroup createPrivateGroup(String name, Author author,
			byte[] salt) {
		try {
			BdfList group = BdfList.of(
					name,
					author.getName(),
					author.getPublicKey(),
					salt
			);
			byte[] descriptor = clientHelper.toByteArray(group);
			Group g = groupFactory.createGroup(CLIENT_ID, descriptor);
			return new PrivateGroup(g, name, author, salt);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public PrivateGroup parsePrivateGroup(Group group) throws FormatException {
		byte[] descriptor = group.getDescriptor();
		BdfList list = clientHelper.toList(descriptor);
		Author a =
				authorFactory.createAuthor(list.getString(1), list.getRaw(2));
		return new PrivateGroup(group, list.getString(0), a, list.getRaw(3));
	}

}
