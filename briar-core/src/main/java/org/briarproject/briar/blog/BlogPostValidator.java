package org.briarproject.briar.blog;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupFactory;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.blog.Blog;
import org.briarproject.briar.api.blog.BlogFactory;
import org.briarproject.briar.api.blog.MessageType;

import java.security.GeneralSecurityException;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_AUTHOR;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_COMMENT;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_ORIGINAL_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_ORIGINAL_PARENT_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_READ;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_RSS_FEED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TIME_RECEIVED;
import static org.briarproject.briar.api.blog.BlogConstants.KEY_TYPE;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_COMMENT_TEXT_LENGTH;
import static org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_TEXT_LENGTH;
import static org.briarproject.briar.api.blog.BlogManager.CLIENT_ID;
import static org.briarproject.briar.api.blog.BlogManager.MAJOR_VERSION;
import static org.briarproject.briar.api.blog.BlogPostFactory.SIGNING_LABEL_COMMENT;
import static org.briarproject.briar.api.blog.BlogPostFactory.SIGNING_LABEL_POST;
import static org.briarproject.briar.api.blog.MessageType.COMMENT;
import static org.briarproject.briar.api.blog.MessageType.POST;

@Immutable
@NotNullByDefault
class BlogPostValidator extends BdfMessageValidator {

	private final GroupFactory groupFactory;
	private final MessageFactory messageFactory;
	private final BlogFactory blogFactory;

	BlogPostValidator(GroupFactory groupFactory, MessageFactory messageFactory,
			BlogFactory blogFactory, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);

		this.groupFactory = groupFactory;
		this.messageFactory = messageFactory;
		this.blogFactory = blogFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		BdfMessageContext c;

		int type = body.getLong(0).intValue();
		body.remove(0);
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
				c = validateWrappedPost(body);
				break;
			case WRAPPED_COMMENT:
				c = validateWrappedComment(body);
				break;
			default:
				throw new InvalidMessageException("Unknown Message Type");
		}
		c.getDictionary().put(KEY_TYPE, type);
		return c;
	}

	private BdfMessageContext validatePost(Message m, Group g, BdfList body)
			throws InvalidMessageException, FormatException {

		// Text, signature
		checkSize(body, 2);
		String text = body.getString(0);
		checkLength(text, 0, MAX_BLOG_POST_TEXT_LENGTH);

		// Verify signature
		byte[] sig = body.getRaw(1);
		checkLength(sig, 1, MAX_SIGNATURE_LENGTH);
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), text);
		Blog b = blogFactory.parseBlog(g);
		Author a = b.getAuthor();
		try {
			clientHelper.verifySignature(sig, SIGNING_LABEL_POST, signed,
					a.getPublicKey());
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_ORIGINAL_MSG_ID, m.getId());
		meta.put(KEY_AUTHOR, clientHelper.toList(a));
		meta.put(KEY_RSS_FEED, b.isRssFeed());
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validateComment(Message m, Group g, BdfList body)
			throws InvalidMessageException, FormatException {

		// Comment, parent original ID, parent ID, signature
		checkSize(body, 4);

		// Comment
		String comment = body.getOptionalString(0);
		checkLength(comment, 1, MAX_BLOG_COMMENT_TEXT_LENGTH);

		// Parent original ID
		// The ID of a post or comment in this blog or another blog
		byte[] pOriginalIdBytes = body.getRaw(1);
		checkLength(pOriginalIdBytes, MessageId.LENGTH);
		MessageId pOriginalId = new MessageId(pOriginalIdBytes);

		// Parent ID
		// The ID of the comment's parent, which is a post, comment, wrapped
		// post or wrapped comment in this blog, which had the ID
		// parentOriginalId in the blog where it was originally posted
		byte[] currentIdBytes = body.getRaw(2);
		checkLength(currentIdBytes, MessageId.LENGTH);
		MessageId currentId = new MessageId(currentIdBytes);

		// Signature
		byte[] sig = body.getRaw(3);
		checkLength(sig, 1, MAX_SIGNATURE_LENGTH);
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), comment,
				pOriginalId, currentId);
		Blog b = blogFactory.parseBlog(g);
		Author a = b.getAuthor();
		try {
			clientHelper.verifySignature(sig, SIGNING_LABEL_COMMENT,
					signed, a.getPublicKey());
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		if (comment != null) meta.put(KEY_COMMENT, comment);
		meta.put(KEY_ORIGINAL_MSG_ID, m.getId());
		meta.put(KEY_ORIGINAL_PARENT_MSG_ID, pOriginalId);
		meta.put(KEY_PARENT_MSG_ID, currentId);
		meta.put(KEY_AUTHOR, clientHelper.toList(a));
		Collection<MessageId> dependencies = singletonList(currentId);
		return new BdfMessageContext(meta, dependencies);
	}

	private BdfMessageContext validateWrappedPost(BdfList body)
			throws InvalidMessageException, FormatException {

		// Copied group descriptor, copied timestamp, copied text, copied
		// signature
		checkSize(body, 4);

		// Copied group descriptor of original post
		byte[] descriptor = body.getRaw(0);

		// Copied timestamp of original post
		long wTimestamp = body.getLong(1);
		if (wTimestamp < 0) throw new FormatException();

		// Copied text of original post
		String text = body.getString(2);
		checkLength(text, 0, MAX_BLOG_POST_TEXT_LENGTH);

		// Copied signature of original post
		byte[] signature = body.getRaw(3);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Reconstruct and validate the original post
		Group wGroup = groupFactory.createGroup(CLIENT_ID, MAJOR_VERSION,
				descriptor);
		Blog wBlog = blogFactory.parseBlog(wGroup);
		BdfList wBodyList = BdfList.of(POST.getInt(), text, signature);
		byte[] wBody = clientHelper.toByteArray(wBodyList);
		Message wMessage =
				messageFactory.createMessage(wGroup.getId(), wTimestamp, wBody);
		wBodyList.remove(0);
		BdfMessageContext c = validatePost(wMessage, wGroup, wBodyList);

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_ORIGINAL_MSG_ID, wMessage.getId());
		meta.put(KEY_TIMESTAMP, wTimestamp);
		meta.put(KEY_AUTHOR, c.getDictionary().getList(KEY_AUTHOR));
		meta.put(KEY_RSS_FEED, wBlog.isRssFeed());
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validateWrappedComment(BdfList body)
			throws InvalidMessageException, FormatException {

		// Copied group descriptor, copied timestamp, copied text, copied
		// parent original ID, copied parent ID, copied signature, parent ID
		checkSize(body, 7);

		// Copied group descriptor of original comment
		byte[] descriptor = body.getRaw(0);

		// Copied timestamp of original comment
		long wTimestamp = body.getLong(1);
		if (wTimestamp < 0) throw new FormatException();

		// Copied text of original comment
		String comment = body.getOptionalString(2);
		checkLength(comment, 1, MAX_BLOG_COMMENT_TEXT_LENGTH);

		// Copied parent original ID of original comment
		byte[] pOriginalIdBytes = body.getRaw(3);
		checkLength(pOriginalIdBytes, MessageId.LENGTH);
		MessageId pOriginalId = new MessageId(pOriginalIdBytes);

		// Copied parent ID of original comment
		byte[] oldIdBytes = body.getRaw(4);
		checkLength(oldIdBytes, MessageId.LENGTH);
		MessageId oldId = new MessageId(oldIdBytes);

		// Copied signature of original comment
		byte[] signature = body.getRaw(5);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Parent ID
		// The ID of this comment's parent, which is a post, comment, wrapped
		// post or wrapped comment in this blog, which had the ID
		// copiedParentOriginalId in the blog where the parent was originally
		// posted, and the ID copiedParentId in the blog where this comment was
		// originally posted
		byte[] parentIdBytes = body.getRaw(6);
		checkLength(parentIdBytes, MessageId.LENGTH);
		MessageId parentId = new MessageId(parentIdBytes);

		// Reconstruct and validate the original comment
		Group wGroup = groupFactory.createGroup(CLIENT_ID, MAJOR_VERSION,
				descriptor);
		BdfList wBodyList = BdfList.of(COMMENT.getInt(), comment, pOriginalId,
				oldId, signature);
		byte[] wBody = clientHelper.toByteArray(wBodyList);
		Message wMessage =
				messageFactory.createMessage(wGroup.getId(), wTimestamp, wBody);
		wBodyList.remove(0);
		BdfMessageContext c = validateComment(wMessage, wGroup, wBodyList);

		// Return the metadata and dependencies
		Collection<MessageId> dependencies = singletonList(parentId);
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_ORIGINAL_MSG_ID, wMessage.getId());
		meta.put(KEY_ORIGINAL_PARENT_MSG_ID, pOriginalId);
		meta.put(KEY_PARENT_MSG_ID, parentId);
		meta.put(KEY_TIMESTAMP, wTimestamp);
		if (comment != null) meta.put(KEY_COMMENT, comment);
		meta.put(KEY_AUTHOR, c.getDictionary().getList(KEY_AUTHOR));
		return new BdfMessageContext(meta, dependencies);
	}

	private void addMessageMetadata(BdfMessageContext c, long time) {
		c.getDictionary().put(KEY_TIMESTAMP, time);
		c.getDictionary().put(KEY_TIME_RECEIVED, clock.currentTimeMillis());
		c.getDictionary().put(KEY_READ, false);
	}

}
