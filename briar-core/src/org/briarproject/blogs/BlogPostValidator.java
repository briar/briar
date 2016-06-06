package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_HAS_BODY;
import static org.briarproject.api.blogs.BlogConstants.KEY_PARENT;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.KEY_TEASER;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.api.blogs.BlogConstants.KEY_TITLE;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_TEASER_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_TITLE_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;

class BlogPostValidator extends BdfMessageValidator {

	private final CryptoComponent crypto;
	private final BlogFactory blogFactory;

	BlogPostValidator(CryptoComponent crypto, BlogFactory blogFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		super(clientHelper, metadataEncoder, clock);

		this.crypto = crypto;
		this.blogFactory = blogFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		// Content, Signature
		checkSize(body, 2);
		BdfList content = body.getList(0);

		// Content: Parent ID, content type, title (optional), teaser,
		//          post body (optional), attachments (optional)
		checkSize(body, 6);
		// Parent ID is optional
		byte[] parent = content.getOptionalRaw(0);
		checkLength(parent, UniqueId.LENGTH);
		// Content type
		String contentType = content.getString(1);
		checkLength(contentType, 0, MAX_CONTENT_TYPE_LENGTH);
		// Blog post title is optional
		String title = content.getOptionalString(2);
		checkLength(contentType, 0, MAX_BLOG_POST_TITLE_LENGTH);
		// Blog teaser
		String teaser = content.getString(3);
		// TODO make sure that there is only text in the teaser
		checkLength(contentType, 0, MAX_BLOG_POST_TEASER_LENGTH);
		// Blog post body is optional
		byte[] postBody = content.getOptionalRaw(4);
		checkLength(postBody, 0, MAX_BLOG_POST_BODY_LENGTH);
		// Attachments
		BdfDictionary attachments = content.getOptionalDictionary(5);
		// TODO handle attachments somehow

		// Signature
		byte[] sig = body.getRaw(1);
		checkLength(sig, 0, MAX_SIGNATURE_LENGTH);
		// Verify the signature
		try {
			// Get the blog author
			Blog b = blogFactory.parseBlog(g, ""); // description doesn't matter
			Author a = b.getAuthor();
			// Parse the public key
			KeyParser keyParser = crypto.getSignatureKeyParser();
			PublicKey key = keyParser.parsePublicKey(a.getPublicKey());
			// Serialise the data to be signed
			BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), content);
			// Verify the signature
			Signature signature = crypto.getSignature();
			signature.initVerify(key);
			signature.update(clientHelper.toByteArray(signed));
			if (!signature.verify(sig)) {
				throw new InvalidMessageException("Invalid signature");
			}
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException("Invalid public key");
		}

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		Collection<MessageId> dependencies = null;
		if (title != null) meta.put(KEY_TITLE, title);
		meta.put(KEY_TEASER, teaser);
		meta.put(KEY_HAS_BODY, postBody != null);
		meta.put(KEY_TIMESTAMP, m.getTimestamp());
		if (parent != null) {
			meta.put(KEY_PARENT, parent);
			dependencies = Collections.singletonList(new MessageId(parent));
		}
		meta.put(KEY_CONTENT_TYPE, contentType);
		meta.put(KEY_READ, false);
		return new BdfMessageContext(meta, dependencies);
	}
}
