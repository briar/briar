package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.blogs.Blog;
import org.briarproject.api.blogs.BlogFactory;
import org.briarproject.api.blogs.MessageType;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PublicKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfEntry;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.Author;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageFactory;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_AUTHOR_NAME;
import static org.briarproject.api.blogs.BlogConstants.KEY_COMMENT;
import static org.briarproject.api.blogs.BlogConstants.KEY_CONTENT_TYPE;
import static org.briarproject.api.blogs.BlogConstants.KEY_CURRENT_MSG_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_ORIGINAL_MSG_ID;
import static org.briarproject.api.blogs.BlogConstants.KEY_PUBLIC_KEY;
import static org.briarproject.api.blogs.BlogConstants.KEY_READ;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.api.blogs.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.api.blogs.BlogConstants.KEY_TITLE;
import static org.briarproject.api.blogs.BlogConstants.KEY_TYPE;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_TITLE_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;

class BlogPostValidator extends BdfMessageValidator {

	private final CryptoComponent crypto;
	private final GroupFactory groupFactory;
	private final MessageFactory messageFactory;
	private final BlogFactory blogFactory;

	BlogPostValidator(CryptoComponent crypto, GroupFactory groupFactory,
			MessageFactory messageFactory, BlogFactory blogFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		super(clientHelper, metadataEncoder, clock);

		this.crypto = crypto;
		this.groupFactory = groupFactory;
		this.messageFactory = messageFactory;
		this.blogFactory = blogFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		BdfMessageContext c;

		// TODO Remove! For Temporary Backwards Compatibility only!
		if (body.get(0) instanceof BdfList) {
			c = validatePost(m, g, body);
			addMessageMetadata(c, m.getTimestamp());
			return c;
		}

		int type = body.getLong(0).intValue();
		body.removeElementAt(0);
		switch (MessageType.valueOf(type)) {
			case POST:
				c = validatePost(m, g, body);
				addMessageMetadata(c, m.getTimestamp());
				break;
			case COMMENT:
				c = validateComment(m, g, body);
				addMessageMetadata(c, m.getTimestamp());
				break;
			case WRAPPED_POST:
				c = validateWrappedPost(m, g, body);
				break;
			case WRAPPED_COMMENT:
				c = validateWrappedComment(m, g, body);
				break;
			default:
				throw new InvalidMessageException("Unknown Message Type");
		}
		c.getDictionary().put(KEY_TYPE, type);
		return c;
	}

	private BdfMessageContext validatePost(Message m, Group g, BdfList body)
			throws InvalidMessageException, FormatException {

		// Content, Signature
		checkSize(body, 2);
		BdfList content = body.getList(0);

		// Content: content type, title (optional), post body,
		//          attachments (optional)
		checkSize(content, 5);
		// Parent ID is optional
		// TODO remove when breaking backwards compatibility
		byte[] parent = content.getOptionalRaw(0);
		checkLength(parent, UniqueId.LENGTH);
		// Content type
		String contentType = content.getString(1);
		checkLength(contentType, 0, MAX_CONTENT_TYPE_LENGTH);
		if (!contentType.equals("text/plain"))
			throw new InvalidMessageException("Invalid content type");
		// Blog post title is optional
		String title = content.getOptionalString(2);
		checkLength(contentType, 0, MAX_BLOG_POST_TITLE_LENGTH);
		// Blog post body
		byte[] postBody = content.getRaw(3);
		checkLength(postBody, 0, MAX_BLOG_POST_BODY_LENGTH);
		// Attachments
		content.getOptionalDictionary(4);

		// Verify Signature
		byte[] sig = body.getRaw(1);
		checkLength(sig, 1, MAX_SIGNATURE_LENGTH);
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), content);
		Blog b = blogFactory.parseBlog(g, ""); // description doesn't matter
		Author a = b.getAuthor();
		verifySignature(sig, a.getPublicKey(), signed);

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		if (title != null) meta.put(KEY_TITLE, title);
		meta.put(KEY_AUTHOR, authorToBdfDictionary(a));
		meta.put(KEY_CONTENT_TYPE, contentType);
		return new BdfMessageContext(meta, null);
	}

	private BdfMessageContext validateComment(Message m, Group g, BdfList body)
			throws InvalidMessageException, FormatException {

		// comment, parent_original_id, signature, parent_current_id
		checkSize(body, 4);

		// Comment
		String comment = body.getOptionalString(0);
		checkLength(comment, 0, MAX_BLOG_POST_BODY_LENGTH);

		// parent_original_id
		// The ID of a post or comment in this group or another group
		byte[] originalIdBytes = body.getRaw(1);
		checkLength(originalIdBytes, MessageId.LENGTH);
		MessageId originalId = new MessageId(originalIdBytes);

		// Signature
		byte[] sig = body.getRaw(2);
		checkLength(sig, 0, MAX_SIGNATURE_LENGTH);
		BdfList signed =
				BdfList.of(g.getId(), m.getTimestamp(), comment, originalId);
		Blog b = blogFactory.parseBlog(g, ""); // description doesn't matter
		Author a = b.getAuthor();
		verifySignature(sig, a.getPublicKey(), signed);

		// parent_current_id
		// The ID of a post, comment, wrapped post or wrapped comment in this
		// group, which had the ID parent_original_id in the group
		// where it was originally posted
		byte[] currentIdBytes = body.getRaw(3);
		checkLength(currentIdBytes, MessageId.LENGTH);
		MessageId currentId = new MessageId(currentIdBytes);

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		if (comment != null) meta.put(KEY_COMMENT, comment);
		meta.put(KEY_ORIGINAL_MSG_ID, originalId);
		meta.put(KEY_CURRENT_MSG_ID, currentId);
		meta.put(KEY_AUTHOR, authorToBdfDictionary(a));
		Collection<MessageId> dependencies = Collections.singleton(currentId);
		return new BdfMessageContext(meta, dependencies);
	}

	private BdfMessageContext validateWrappedPost(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		// group descriptor, timestamp, content, signature
		checkSize(body, 4);

		// Group Descriptor
		byte[] descriptor = body.getRaw(0);

		// Timestamp of Wrapped Post
		long wTimestamp = body.getLong(1);

		// Content of Wrapped Post
		BdfList content = body.getList(2);

		// Signature of Wrapped Post
		byte[] signature = body.getRaw(3);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Get and Validate the Wrapped Message
		Group wGroup = groupFactory
				.createGroup(BlogManagerImpl.CLIENT_ID, descriptor);
		BdfList wBodyList = BdfList.of(content, signature);
		byte[] wBody = clientHelper.toByteArray(wBodyList);
		Message wMessage =
				messageFactory.createMessage(wGroup.getId(), wTimestamp, wBody);
		BdfMessageContext c = validatePost(wMessage, wGroup, wBodyList);

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_TIMESTAMP, wTimestamp);
		meta.put(KEY_AUTHOR, c.getDictionary().getDictionary(KEY_AUTHOR));
		meta.put(KEY_CONTENT_TYPE,
				c.getDictionary().getString(KEY_CONTENT_TYPE));
		return new BdfMessageContext(meta, null);
	}

	private BdfMessageContext validateWrappedComment(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		// group descriptor, timestamp, comment, parent_original_id, signature,
		// parent_current_id
		checkSize(body, 6);

		// Group Descriptor
		byte[] descriptor = body.getRaw(0);

		// Timestamp of Wrapped Comment
		long wTimestamp = body.getLong(1);

		// Body of Wrapped Comment
		String comment = body.getOptionalString(2);

		// parent_original_id
		// Taken from the original comment
		byte[] originalIdBytes = body.getRaw(3);
		checkLength(originalIdBytes, MessageId.LENGTH);
		MessageId originalId = new MessageId(originalIdBytes);

		// signature
		// Taken from the original comment
		byte[] signature = body.getRaw(4);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// parent_current_id
		// The ID of a post, comment, wrapped post or wrapped comment in this
		// group, which had the ID parent_original_id in the group
		// where it was originally posted
		byte[] currentIdBytes = body.getRaw(5);
		checkLength(currentIdBytes, MessageId.LENGTH);
		MessageId currentId = new MessageId(currentIdBytes);

		// Get and Validate the Wrapped Comment
		Group wGroup = groupFactory
				.createGroup(BlogManagerImpl.CLIENT_ID, descriptor);
		BdfList wBodyList =	BdfList.of(comment, originalId, signature,
				currentId);
		byte[] wBody = clientHelper.toByteArray(wBodyList);
		Message wMessage =
				messageFactory.createMessage(wGroup.getId(), wTimestamp, wBody);
		BdfMessageContext c = validateComment(wMessage, wGroup, wBodyList);

		// Return the metadata and dependencies
		Collection<MessageId> dependencies = Collections.singleton(currentId);
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_ORIGINAL_MSG_ID, wMessage.getId());
		meta.put(KEY_CURRENT_MSG_ID, currentId);
		meta.put(KEY_TIMESTAMP, wTimestamp);
		if (comment != null) meta.put(KEY_COMMENT, comment);
		meta.put(KEY_AUTHOR, c.getDictionary().getDictionary(KEY_AUTHOR));
		return new BdfMessageContext(meta, dependencies);
	}

	private void verifySignature(byte[] sig, byte[] publicKey, BdfList signed)
			throws InvalidMessageException {
		try {
			// Parse the public key
			KeyParser keyParser = crypto.getSignatureKeyParser();
			PublicKey key = keyParser.parsePublicKey(publicKey);
			// Verify the signature
			Signature signature = crypto.getSignature();
			signature.initVerify(key);
			signature.update(clientHelper.toByteArray(signed));
			if (!signature.verify(sig)) {
				throw new InvalidMessageException("Invalid signature");
			}
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException("Invalid public key");
		} catch (FormatException e) {
			throw new InvalidMessageException(e);
		}
	}

	static BdfDictionary authorToBdfDictionary(Author a) {
		return BdfDictionary.of(
				new BdfEntry(KEY_AUTHOR_ID, a.getId()),
				new BdfEntry(KEY_AUTHOR_NAME, a.getName()),
				new BdfEntry(KEY_PUBLIC_KEY, a.getPublicKey())
		);
	}

	private void addMessageMetadata(BdfMessageContext c, long time) {
		c.getDictionary().put(KEY_TIMESTAMP, time);
		c.getDictionary().put(KEY_TIME_RECEIVED, clock.currentTimeMillis());
		c.getDictionary().put(KEY_READ, false);
	}

}
