package org.briarproject.briar.api.avatar;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;

import java.io.IOException;
import java.io.InputStream;

public interface AvatarMessageEncoder {
	/**
	 * Returns an update message and its metadata.
	 */
	Pair<Message, BdfDictionary> encodeUpdateMessage(GroupId groupId,
			long version, String contentType, InputStream in)
			throws IOException;
}
