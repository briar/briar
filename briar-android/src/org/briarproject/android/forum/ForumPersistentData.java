package org.briarproject.android.forum;

import org.briarproject.api.clients.MessageTree;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumPostHeader;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.briarproject.clients.MessageTreeImpl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a singleton that defines the data that should persist, i.e.
 * still be present in memory after activity restarts. This class is not thread
 * safe.
 */
public class ForumPersistentData {

	private volatile MessageTree<ForumPostHeader> tree =
			new MessageTreeImpl<>();
	private volatile Map<MessageId, byte[]> bodyCache = new HashMap<>();
	private volatile LocalAuthor localAuthor;
	private volatile Forum forum;
	private volatile GroupId groupId;
	private List<ForumEntry> forumEntries;

	void clearAll() {
		clearForumEntries();
		tree.clear();
		bodyCache.clear();
		localAuthor = null;
		forum = null;
		groupId = null;
	}

	void clearForumEntries() {
		forumEntries = null;
	}

	void addHeaders(Collection<ForumPostHeader> headers) {
		tree.add(headers);
	}

	void addHeader(ForumPostHeader header) {
		tree.add(header);
	}

	public Collection<ForumPostHeader> getHeaders() {
		return tree.depthFirstOrder();
	}

	void addBody(MessageId messageId, byte[] body) {
		bodyCache.put(messageId, body);
	}

	byte[] getBody(MessageId messageId) {
		return bodyCache.get(messageId);
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	public void setLocalAuthor(
			LocalAuthor localAuthor) {
		this.localAuthor = localAuthor;
	}

	public Forum getForum() {
		return forum;
	}

	public void setForum(Forum forum) {
		this.forum = forum;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public void setGroupId(GroupId groupId) {
		this.groupId = groupId;
	}

	List<ForumEntry> getForumEntries() {
		return forumEntries;
	}

	void setForumEntries(List<ForumEntry> forumEntries) {
		this.forumEntries = forumEntries;
	}
}
