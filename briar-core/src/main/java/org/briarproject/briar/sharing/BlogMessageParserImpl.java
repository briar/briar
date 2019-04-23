package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class BlogMessageParserImpl extends MessageParserImpl<Blog> {

	private final BlogFactory blogFactory;

	@Inject
	BlogMessageParserImpl(ClientHelper clientHelper, BlogFactory blogFactory) {
		super(clientHelper);
		this.blogFactory = blogFactory;
	}

	@Override
	public Blog createShareable(BdfList descriptor) throws FormatException {
		// Author, RSS
		BdfList authorList = descriptor.getList(0);
		boolean rssFeed = descriptor.getBoolean(1);

		Author author = clientHelper.parseAndValidateAuthor(authorList);
		if (rssFeed) return blogFactory.createFeedBlog(author);
		else return blogFactory.createBlog(author);
	}

}
