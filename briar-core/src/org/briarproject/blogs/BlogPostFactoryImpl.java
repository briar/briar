package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.blogs.MessageType.COMMENT;
import static org.briarproject.api.blogs.MessageType.POST;
import static org.briarproject.api.blogs.MessageType.WRAPPED_COMMENT;
import static org.briarproject.api.blogs.MessageType.WRAPPED_POST;

class BlogPostFactoryImpl implements BlogPostFactory {

	private final ClientHelper clientHelper;
	private final Clock clock;

	@Inject
	BlogPostFactoryImpl(ClientHelper clientHelper, Clock clock) {
		this.clientHelper = clientHelper;
		this.clock = clock;
	}

	@Override
	public BlogPost createBlogPost(@NotNull GroupId groupId, long timestamp,
			@Nullable MessageId parent, @NotNull LocalAuthor author,
			@NotNull String body)
			throws FormatException, GeneralSecurityException {

		// Validate the arguments
		if (body.length() > MAX_BLOG_POST_BODY_LENGTH)
			throw new IllegalArgumentException();

		// Serialise the data to be signed
		BdfList signed = BdfList.of(groupId, timestamp, body);

		// Generate the signature
		byte[] sig = clientHelper.sign(signed, author.getPrivateKey());

		// Serialise the signed message
		BdfList message = BdfList.of(POST.getInt(), body, sig);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new BlogPost(m, parent, author);
	}

	@Override
	public Message createBlogComment(GroupId groupId, LocalAuthor author,
			@Nullable String comment, MessageId originalId, MessageId wrappedId)
			throws FormatException, GeneralSecurityException {

		long timestamp = clock.currentTimeMillis();

		// Generate the signature
		BdfList signed =
				BdfList.of(groupId, timestamp, comment, originalId, wrappedId);
		byte[] sig = clientHelper.sign(signed, author.getPrivateKey());

		// Serialise the signed message
		BdfList message =
				BdfList.of(COMMENT.getInt(), comment, originalId, wrappedId,
						sig);
		return clientHelper.createMessage(groupId, timestamp, message);
	}

	@Override
	public Message createWrappedPost(GroupId groupId, byte[] descriptor,
			long timestamp, BdfList body)
			throws FormatException {

		// Serialise the message
		String content = body.getString(1);
		byte[] signature = body.getRaw(2);
		BdfList message =
				BdfList.of(WRAPPED_POST.getInt(), descriptor, timestamp,
						content, signature);
		return clientHelper
				.createMessage(groupId, clock.currentTimeMillis(), message);
	}

	@Override
	public Message createWrappedPost(GroupId groupId, BdfList body)
			throws FormatException {

		// Serialise the message
		byte[] descriptor = body.getRaw(1);
		long timestamp = body.getLong(2);
		String content = body.getString(3);
		byte[] signature = body.getRaw(4);
		BdfList message =
				BdfList.of(WRAPPED_POST.getInt(), descriptor, timestamp,
						content, signature);
		return clientHelper
				.createMessage(groupId, clock.currentTimeMillis(), message);
	}

	@Override
	public Message createWrappedComment(GroupId groupId, byte[] descriptor,
			long timestamp, BdfList body, MessageId currentId)
			throws FormatException {

		// Serialise the message
		String comment = body.getOptionalString(1);
		byte[] originalId = body.getRaw(2);
		byte[] oldId = body.getRaw(3);
		byte[] signature = body.getRaw(4);
		BdfList message =
				BdfList.of(WRAPPED_COMMENT.getInt(), descriptor, timestamp,
						comment, originalId, oldId, signature, currentId);
		return clientHelper
				.createMessage(groupId, clock.currentTimeMillis(), message);
	}

	@Override
	public Message createWrappedComment(GroupId groupId, BdfList body,
			MessageId currentId) throws FormatException {

		// Serialise the message
		byte[] descriptor = body.getRaw(1);
		long timestamp = body.getLong(2);
		String comment = body.getOptionalString(3);
		byte[] originalId = body.getRaw(4);
		byte[] oldId = body.getRaw(5);
		byte[] signature = body.getRaw(6);
		BdfList message =
				BdfList.of(WRAPPED_COMMENT.getInt(), descriptor, timestamp,
						comment, originalId, oldId, signature, currentId);
		return clientHelper
				.createMessage(groupId, clock.currentTimeMillis(), message);
	}

}
