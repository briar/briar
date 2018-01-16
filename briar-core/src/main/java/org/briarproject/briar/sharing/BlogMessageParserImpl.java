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

import static org.briarproject.bramble.api.identity.Author.FORMAT_VERSION;

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
	protected Blog createShareable(BdfList descriptor) throws FormatException {
		// Author, RSS
		BdfList authorList = descriptor.getList(0);
		boolean rssFeed = descriptor.getBoolean(1);

		// Format version, name, public key
		int formatVersion = authorList.getLong(0).intValue();
		if (formatVersion != FORMAT_VERSION) throw new FormatException();
		String name = authorList.getString(1);
		byte[] publicKey = authorList.getRaw(2);

		Author author = authorFactory.createAuthor(formatVersion, name,
				publicKey);
		if (rssFeed) return blogFactory.createFeedBlog(author);
		else return blogFactory.createBlog(author);
	}

}
