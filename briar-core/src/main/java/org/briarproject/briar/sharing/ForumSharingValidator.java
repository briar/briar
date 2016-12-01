package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.client.SessionId;
import org.briarproject.briar.client.BdfQueueMessageValidator;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.briar.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.INVITATION_MSG;
import static org.briarproject.briar.api.sharing.SharingConstants.LOCAL;
import static org.briarproject.briar.api.sharing.SharingConstants.MAX_INVITATION_MESSAGE_LENGTH;
import static org.briarproject.briar.api.sharing.SharingConstants.SESSION_ID;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ABORT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_ACCEPT;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_DECLINE;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.briar.api.sharing.SharingConstants.SHARE_MSG_TYPE_LEAVE;
import static org.briarproject.briar.api.sharing.SharingConstants.TIME;
import static org.briarproject.briar.api.sharing.SharingConstants.TYPE;

@Immutable
@NotNullByDefault
class ForumSharingValidator extends BdfQueueMessageValidator {

	@Inject
	ForumSharingValidator(ClientHelper clientHelper,
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
			checkSize(body, 4, 5);

			String name = body.getString(2);
			checkLength(name, 1, MAX_FORUM_NAME_LENGTH);

			byte[] salt = body.getRaw(3);
			checkLength(salt, FORUM_SALT_LENGTH);

			d.put(FORUM_NAME, name);
			d.put(FORUM_SALT, salt);

			if (body.size() > 4) {
				String msg = body.getString(4);
				checkLength(msg, 0, MAX_INVITATION_MESSAGE_LENGTH);
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
