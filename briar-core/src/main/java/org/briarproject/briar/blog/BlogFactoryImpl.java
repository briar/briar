package org.briarproject.briar.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;

@Immutable
@NotNullByDefault
class BlogFactoryImpl implements BlogFactory {

	private final GroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final ClientHelper clientHelper;

	@Inject
	BlogFactoryImpl(GroupFactory groupFactory, AuthorFactory authorFactory,
			ClientHelper clientHelper) {

		this.groupFactory = groupFactory;
		this.authorFactory = authorFactory;
		this.clientHelper = clientHelper;
	}

	@Override
	public Blog createBlog(Author a) {
		return createBlog(a, false);
	}

	@Override
	public Blog createFeedBlog(Author a) {
		return createBlog(a, true);
	}

	private Blog createBlog(Author a, boolean rssFeed) {
		try {
			BdfList blog = BdfList.of(
					a.getName(),
					a.getPublicKey(),
					rssFeed
			);
			byte[] descriptor = clientHelper.toByteArray(blog);
			Group g = groupFactory
					.createGroup(BlogManagerImpl.CLIENT_ID, descriptor);
			return new Blog(g, a, rssFeed);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Blog parseBlog(Group group) throws FormatException {
		byte[] descriptor = group.getDescriptor();
		// Author name, public key, RSS feed
		BdfList blog = clientHelper.toList(descriptor);
		String name = blog.getString(0);
		if (name.length() > MAX_AUTHOR_NAME_LENGTH)
			throw new IllegalArgumentException();
		byte[] publicKey = blog.getRaw(1);
		if (publicKey.length > MAX_PUBLIC_KEY_LENGTH)
			throw new IllegalArgumentException();

		Author author = authorFactory.createAuthor(name, publicKey);
		boolean rssFeed = blog.getBoolean(2);
		return new Blog(group, author, rssFeed);
	}

}
