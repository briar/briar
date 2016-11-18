package org.briarproject.api.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.CryptoExecutor;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.security.GeneralSecurityException;

import static org.briarproject.api.forum.ForumManager.CLIENT_ID;

public interface ForumPostFactory {

	String SIGNING_LABEL_POST = CLIENT_ID + "/POST";

	@CryptoExecutor
	ForumPost createPost(GroupId groupId, long timestamp, MessageId parent,
			LocalAuthor author, String body)
			throws FormatException, GeneralSecurityException;

}
