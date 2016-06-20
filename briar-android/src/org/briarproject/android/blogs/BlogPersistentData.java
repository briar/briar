package org.briarproject.android.blogs;

import org.briarproject.api.sync.GroupId;

import java.util.Collection;
import java.util.TreeSet;

import javax.inject.Inject;

/**
 * This class is a singleton that defines the data that should persist, i.e.
 * still be present in memory after activity restarts. This class is not thread
 * safe.
 */
public class BlogPersistentData {

	private volatile GroupId groupId;
	private volatile TreeSet<BlogPostItem> posts = new TreeSet<>();

	public BlogPersistentData() {

	}

	public void setGroupId(GroupId groupId) {
		this.groupId = groupId;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public void setPosts(Collection<BlogPostItem> posts) {
		this.posts.clear();
		this.posts.addAll(posts);
	}

	void addPost(BlogPostItem post) {
		posts.add(post);
	}

	TreeSet<BlogPostItem> getBlogPosts() {
		return posts;
	}

	void clearAll() {
		groupId = null;
		posts.clear();
	}
}
