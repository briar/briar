package org.briarproject.briar.sharing;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.BlogFactory;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.util.ValidationUtils.checkSize;

@Immutable
@NotNullByDefault
class BlogSharingValidator extends SharingValidator {

	private final BlogFactory blogFactory;

	BlogSharingValidator(MessageEncoder messageEncoder,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, BlogFactory blogFactory) {
		super(messageEncoder, clientHelper, metadataEncoder, clock);
		this.blogFactory = blogFactory;
	}

	@Override
	protected GroupId validateDescriptor(BdfList descriptor)
			throws FormatException {
		// Author, RSS
		checkSize(descriptor, 2);
		BdfList authorList = descriptor.getList(0);
		boolean rssFeed = descriptor.getBoolean(1);
		Author author = clientHelper.parseAndValidateAuthor(authorList);
		if (rssFeed) return blogFactory.createFeedBlog(author).getId();
		else return blogFactory.createBlog(author).getId();
	}

}
