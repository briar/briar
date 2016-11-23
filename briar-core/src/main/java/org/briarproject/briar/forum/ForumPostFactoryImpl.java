package org.briarproject.briar.forum;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.util.StringUtils;
import org.briarproject.briar.api.forum.ForumPost;
import org.briarproject.briar.api.forum.ForumPostFactory;

import java.security.GeneralSecurityException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;

@Immutable
@NotNullByDefault
class ForumPostFactoryImpl implements ForumPostFactory {

	private final ClientHelper clientHelper;

	@Inject
	ForumPostFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public ForumPost createPost(GroupId groupId, long timestamp,
			@Nullable MessageId parent, LocalAuthor author, String body)
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
		byte[] sig = clientHelper.sign(SIGNING_LABEL_POST, signed,
				author.getPrivateKey());
		// Serialise the signed message
		BdfList message = BdfList.of(parent, authorList, body, sig);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new ForumPost(m, parent, author);
	}

}
