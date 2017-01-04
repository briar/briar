package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class BlogMessageParserImpl extends MessageParserImpl<Blog> {

	private final BlogFactory blogFactory;
	private final AuthorFactory authorFactory;

	@Inject
	BlogMessageParserImpl(ClientHelper clientHelper, BlogFactory blogFactory,
			AuthorFactory authorFactory) {
		super(clientHelper);
		this.blogFactory = blogFactory;
		this.authorFactory = authorFactory;
	}

	@Override
	protected Blog createShareable(BdfList descriptor)
			throws FormatException {
		String name = descriptor.getString(0);
		byte[] publicKey = descriptor.getRaw(1);
		Author author = authorFactory.createAuthor(name, publicKey);
		return blogFactory.createBlog(author);
	}

}
