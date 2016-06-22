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
import java.util.logging.Logger;

/**
 * This class is a singleton that defines the data that should persist, i.e.
 * still be present in memory after activity restarts. This class is not thread
 * safe.
 */
public class ForumPersistentData {

	protected volatile MessageTree<ForumPostHeader> tree =
			new MessageTreeImpl<>();
	private volatile Map<MessageId, byte[]> bodyCache = new HashMap<>();
	private volatile LocalAuthor localAuthor;
	private volatile Forum forum;
	private volatile GroupId groupId;
	private List<ForumEntry> forumEntries;

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());


	public void clearAll() {
		clearHeaders();
		bodyCache.clear();
		localAuthor = null;
		forum = null;
		groupId = null;
	}

	public void clearHeaders() {
		tree.clear();
		forumEntries = null;
	}

	public void addHeaders(Collection<ForumPostHeader> headers) {
		tree.add(headers);
	}

	public Collection<ForumPostHeader> getHeaders() {
		return tree.depthFirstOrder();
	}

	public void addBody(MessageId messageId, byte[] body) {
		bodyCache.put(messageId, body);
	}

	public byte[] getBody(MessageId messageId) {
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

	public List<ForumEntry> getForumEntries() {
		return forumEntries;
	}

	public void setForumEntries(List<ForumEntry> forumEntries) {
		this.forumEntries = forumEntries;
	}
}
