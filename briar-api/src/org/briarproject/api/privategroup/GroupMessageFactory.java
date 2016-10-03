package org.briarproject.api.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

import java.security.GeneralSecurityException;

public interface GroupMessageFactory {

	@NotNull
	GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			MessageId parent, LocalAuthor author, String body)
			throws FormatException, GeneralSecurityException;

}
