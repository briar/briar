package net.sf.briar.api.messaging;

public class GroupStatus {

	private final Group group;
	private final boolean subscribed, visibleToAll;

	public GroupStatus(Group group, boolean subscribed, boolean visibleToAll) {
		this.group = group;
		this.subscribed = subscribed;
		this.visibleToAll = visibleToAll;
	}

	public Group getGroup() {
		return group;
	}

	public boolean isSubscribed() {
		return subscribed;
	}

	public boolean isVisibleToAll() {
		return visibleToAll;
	}
}
