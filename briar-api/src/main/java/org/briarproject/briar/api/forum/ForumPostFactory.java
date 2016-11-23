package org.briarproject.briar.api.forum;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;

import java.security.GeneralSecurityException;

import javax.annotation.Nullable;

import static org.briarproject.briar.api.forum.ForumManager.CLIENT_ID;

@NotNullByDefault
public interface ForumPostFactory {

	String SIGNING_LABEL_POST = CLIENT_ID + "/POST";

	@CryptoExecutor
	ForumPost createPost(GroupId groupId, long timestamp,
			@Nullable MessageId parent, LocalAuthor author, String body)
			throws FormatException, GeneralSecurityException;

}
