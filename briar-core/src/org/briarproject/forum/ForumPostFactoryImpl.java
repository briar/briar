package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;

class ForumPostFactoryImpl implements ForumPostFactory {

	private final ClientHelper clientHelper;

	@Inject
	ForumPostFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public ForumPost createPost(GroupId groupId, long timestamp,
			MessageId parent, LocalAuthor author, String body)
			throws FormatException, GeneralSecurityException {
		// Validate the arguments
		if (StringUtils.utf8IsTooLong(body, MAX_FORUM_POST_BODY_LENGTH))
			throw new IllegalArgumentException();
		// Serialise the data to be signed
		BdfList authorList =
				BdfList.of(author.getName(), author.getPublicKey());
		BdfList signed = BdfList.of(groupId, timestamp, parent, authorList,
				body);
		// Sign the data
		byte[] sig = clientHelper
				.sign(SIGNING_LABEL_POST, signed, author.getPrivateKey());
		// Serialise the signed message
		BdfList message = BdfList.of(parent, authorList, body, sig);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new ForumPost(m, parent, author);
	}

}
