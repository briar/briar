package org.briarproject.blogs;

import org.briarproject.api.FormatException;
import org.briarproject.api.blogs.BlogPost;
import org.briarproject.api.blogs.BlogPostFactory;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_BODY_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_BLOG_POST_TITLE_LENGTH;
import static org.briarproject.api.blogs.BlogConstants.MAX_CONTENT_TYPE_LENGTH;

class BlogPostFactoryImpl implements BlogPostFactory {

	private final CryptoComponent crypto;
	private final ClientHelper clientHelper;

	@Inject
	BlogPostFactoryImpl(CryptoComponent crypto, ClientHelper clientHelper) {
		this.crypto = crypto;
		this.clientHelper = clientHelper;
	}

	@Override
	public BlogPost createBlogPost(@NotNull GroupId groupId,
			@Nullable String title, long timestamp,
			@Nullable MessageId parent,	@NotNull LocalAuthor author,
			@NotNull String contentType, @NotNull byte[] body)
			throws FormatException, GeneralSecurityException {

		// Validate the arguments
		if (title != null &&
				StringUtils.toUtf8(title).length > MAX_BLOG_POST_TITLE_LENGTH)
			throw new IllegalArgumentException();
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_BLOG_POST_BODY_LENGTH)
			throw new IllegalArgumentException();

		// Serialise the data to be signed
		BdfList content = BdfList.of(parent, contentType, title, body, null);
		BdfList signed = BdfList.of(groupId, timestamp, content);

		// Generate the signature
		Signature signature = crypto.getSignature();
		KeyParser keyParser = crypto.getSignatureKeyParser();
		PrivateKey privateKey =
				keyParser.parsePrivateKey(author.getPrivateKey());
		signature.initSign(privateKey);
		signature.update(clientHelper.toByteArray(signed));
		byte[] sig = signature.sign();

		// Serialise the signed message
		BdfList message = BdfList.of(content, sig);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new BlogPost(title, m, parent, author, contentType);
	}
}
