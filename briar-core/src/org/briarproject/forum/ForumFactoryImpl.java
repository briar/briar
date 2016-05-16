package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.util.StringUtils;

import java.security.SecureRandom;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;

class ForumFactoryImpl implements ForumFactory {

	private final GroupFactory groupFactory;
	private final ClientHelper clientHelper;
	private final SecureRandom random;

	@Inject
	ForumFactoryImpl(GroupFactory groupFactory, ClientHelper clientHelper,
			SecureRandom random) {

		this.groupFactory = groupFactory;
		this.clientHelper = clientHelper;
		this.random = random;
	}

	@Override
	public Forum createForum(String name) {
		int length = StringUtils.toUtf8(name).length;
		if (length == 0) throw new IllegalArgumentException();
		if (length > MAX_FORUM_NAME_LENGTH)
			throw new IllegalArgumentException();
		byte[] salt = new byte[FORUM_SALT_LENGTH];
		random.nextBytes(salt);
		return createForum(name, salt);
	}

	@Override
	public Forum createForum(String name, byte[] salt) {
		try {
			BdfList forum = BdfList.of(name, salt);
			byte[] descriptor = clientHelper.toByteArray(forum);
			Group g = groupFactory
					.createGroup(ForumManagerImpl.CLIENT_ID, descriptor);
			return new Forum(g, name, salt);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

}
