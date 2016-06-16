package org.briarproject.sharing;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import javax.inject.Inject;

import static org.briarproject.api.blogs.BlogConstants.BLOG_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.BLOG_DESC;
import static org.briarproject.api.blogs.BlogConstants.BLOG_TITLE;
import static org.briarproject.api.blogs.BlogConstants.BLOG_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_DESC_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_TITLE_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.api.sharing.SharingConstants.LOCAL;
import static org.briarproject.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.api.sharing.SharingConstants.TIME;
import static org.briarproject.api.sharing.SharingConstants.TYPE;
import static org.briarproject.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;

class BlogSharingValidator extends BdfMessageValidator {

	@Inject
	BlogSharingValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {

		BdfDictionary d = new BdfDictionary();
		long type = body.getLong(0);
		byte[] id = body.getRaw(1);
		checkLength(id, SessionId.LENGTH);

		if (type == SHARE_MSG_TYPE_INVITATION) {
			checkSize(body, 5, 6);

			String name = body.getString(2);
			checkLength(name, 1, MAX_BLOG_TITLE_LENGTH);

			String desc = body.getString(3);
			checkLength(desc, 1, MAX_BLOG_DESC_LENGTH);

			BdfList author = body.getList(4);
			checkSize(author, 2);

			String authorName = author.getString(0);
			checkLength(name, 1, MAX_AUTHOR_NAME_LENGTH);

			byte[] publicKey = author.getRaw(1);
			checkLength(publicKey, 1, MAX_PUBLIC_KEY_LENGTH);

			d.put(BLOG_TITLE, name);
			d.put(BLOG_DESC, desc);
			d.put(BLOG_AUTHOR_NAME, authorName);
			d.put(BLOG_PUBLIC_KEY, publicKey);

			if (body.size() > 5) {
				String msg = body.getString(5);
				checkLength(msg, 0, MAX_MESSAGE_BODY_LENGTH);
				d.put(INVITATION_MSG, msg);
			}
		} else {
			checkSize(body, 2);
			if (type != SHARE_MSG_TYPE_ACCEPT &&
					type != SHARE_MSG_TYPE_DECLINE &&
					type != SHARE_MSG_TYPE_LEAVE &&
					type != SHARE_MSG_TYPE_ABORT) {
				throw new FormatException();
			}
		}
		// Return the metadata
		d.put(TYPE, type);
		d.put(SESSION_ID, id);
		d.put(LOCAL, false);
		d.put(TIME, m.getTimestamp());
		return new BdfMessageContext(d);
	}
}
