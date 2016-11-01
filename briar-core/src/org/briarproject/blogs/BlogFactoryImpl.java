package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

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
		try {
			BdfList blog = BdfList.of(
					a.getName(),
					a.getPublicKey()
			);
			byte[] descriptor = clientHelper.toByteArray(blog);
			Group g = groupFactory
					.createGroup(BlogManagerImpl.CLIENT_ID, descriptor);
			return new Blog(g, a);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Blog parseBlog(@NotNull Group g) throws FormatException {
		byte[] descriptor = g.getDescriptor();
		// Author Name, Public Key
		BdfList blog = clientHelper.toList(descriptor);
		Author a =
				authorFactory.createAuthor(blog.getString(0), blog.getRaw(1));
		return new Blog(g, a);
	}

}
