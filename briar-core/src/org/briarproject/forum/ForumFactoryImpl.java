package org.briarproject.forum;

import com.google.inject.Inject;

import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumFactory;
import org.briarproject.api.sync.GroupFactory;

// Temporary facade during sync protocol refactoring
class ForumFactoryImpl implements ForumFactory {

	private final GroupFactory groupFactory;

	@Inject
	ForumFactoryImpl(GroupFactory groupFactory) {
		this.groupFactory = groupFactory;
	}

	public Forum createForum(String name) {
		return new ForumImpl(groupFactory.createGroup(name));
	}
}
