package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import static org.briarproject.api.forum.ForumConstants.CONTACT_ID;
import static org.briarproject.api.forum.ForumConstants.FORUM_ID;
import static org.briarproject.api.forum.ForumConstants.FORUM_NAME;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT;
import static org.briarproject.api.forum.ForumConstants.GROUP_ID;
import static org.briarproject.api.forum.ForumConstants.IS_SHARER;
import static org.briarproject.api.forum.ForumConstants.SESSION_ID;
import static org.briarproject.api.forum.ForumConstants.STATE;
import static org.briarproject.api.forum.ForumConstants.STORAGE_ID;

// This class is not thread-safe
public abstract class ForumSharingSessionState {

	private final SessionId sessionId;
	private final MessageId storageId;
	private final GroupId groupId;
	private final ContactId contactId;
	private final GroupId forumId;
	private final String forumName;
	private final byte[] forumSalt;
	private int task = -1; // TODO get rid of task, see #376

	public ForumSharingSessionState(SessionId sessionId, MessageId storageId,
			GroupId groupId, ContactId contactId, GroupId forumId,
			String forumName, byte[] forumSalt) {

		this.sessionId = sessionId;
		this.storageId = storageId;
		this.groupId = groupId;
		this.contactId = contactId;
		this.forumId = forumId;
		this.forumName = forumName;
		this.forumSalt = forumSalt;
	}

	public static ForumSharingSessionState fromBdfDictionary(BdfDictionary d)
			throws FormatException{

		SessionId sessionId = new SessionId(d.getRaw(SESSION_ID));
		MessageId messageId = new MessageId(d.getRaw(STORAGE_ID));
		GroupId groupId = new GroupId(d.getRaw(GROUP_ID));
		ContactId contactId = new ContactId(d.getLong(CONTACT_ID).intValue());
		GroupId forumId = new GroupId(d.getRaw(FORUM_ID));
		String forumName = d.getString(FORUM_NAME);
		byte[] forumSalt = d.getRaw(FORUM_SALT);

		int intState = d.getLong(STATE).intValue();
		if (d.getBoolean(IS_SHARER)) {
			SharerSessionState.State state =
					SharerSessionState.State.fromValue(intState);
			return new SharerSessionState(sessionId, messageId, groupId, state,
					contactId, forumId, forumName, forumSalt);
		} else {
			InviteeSessionState.State state =
					InviteeSessionState.State.fromValue(intState);
			return new InviteeSessionState(sessionId, messageId, groupId, state,
					contactId, forumId, forumName, forumSalt);
		}
	}

	public BdfDictionary toBdfDictionary() {
		BdfDictionary d = new BdfDictionary();
		d.put(SESSION_ID, getSessionId());
		d.put(STORAGE_ID, getStorageId());
		d.put(GROUP_ID, getGroupId());
		d.put(CONTACT_ID, getContactId().getInt());
		d.put(FORUM_ID, getForumId());
		d.put(FORUM_NAME, getForumName());
		d.put(FORUM_SALT, getForumSalt());

		return d;
	}

	public SessionId getSessionId() {
		return sessionId;
	}

	public MessageId getStorageId() {
		return storageId;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public ContactId getContactId() {
		return contactId;
	}

	public GroupId getForumId() {
		return forumId;
	}

	public String getForumName() {
		return forumName;
	}

	public byte[] getForumSalt() {
		return forumSalt;
	}

	public void setTask(int task) {
		this.task = task;
	}

	public int getTask() {
		return task;
	}

}