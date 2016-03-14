package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;

class ForumListValidator extends BdfMessageValidator {

	ForumListValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfDictionary validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		// Version, forum list
		checkSize(body, 2);
		// Version
		long version = body.getLong(0);
		if (version < 0) throw new FormatException();
		// Forum list
		BdfList forumList = body.getList(1);
		for (int i = 0; i < forumList.size(); i++) {
			BdfList forum = forumList.getList(i);
			// Name, salt
			checkSize(forum, 2);
			String name = forum.getString(0);
			checkLength(name, 1, MAX_FORUM_NAME_LENGTH);
			byte[] salt = forum.getRaw(1);
			checkLength(salt, FORUM_SALT_LENGTH);
		}
		// Return the metadata
		BdfDictionary meta = new BdfDictionary();
		meta.put("version", version);
		meta.put("local", false);
		return meta;
	}
}
