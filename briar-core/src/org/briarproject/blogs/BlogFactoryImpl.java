package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;

import static org.briarproject.api.blogs.BlogConstants.PERSONAL_BLOG_NAME;

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
	public Blog createBlog(@NotNull String name, @NotNull String description,
			@NotNull Author author) {
		return createBlog(name, description, author, false);
	}

	@Override
	public Blog createPersonalBlog(@NotNull Author a) {
		return createBlog(PERSONAL_BLOG_NAME, "", a, true);
	}

	private Blog createBlog(@NotNull String name, @NotNull String description,
			@NotNull Author author, boolean permanent) {
		try {
			BdfList blog = BdfList.of(
					name,
					author.getName(),
					author.getPublicKey()
			);
			byte[] descriptor = clientHelper.toByteArray(blog);
			Group g = groupFactory
					.createGroup(BlogManagerImpl.CLIENT_ID, descriptor);
			return new Blog(g, name, description, author, permanent);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Blog parseBlog(@NotNull Group g, @NotNull String description)
			throws FormatException {

		byte[] descriptor = g.getDescriptor();
		// Blog Name, Author Name, Public Key
		BdfList blog = clientHelper.toList(descriptor, 0, descriptor.length);
		Author a =
				authorFactory.createAuthor(blog.getString(1), blog.getRaw(2));
		// TODO change permanent depending on how this will be used
		boolean permanent = true;
		return new Blog(g, blog.getString(0), description, a, permanent);
	}

}
