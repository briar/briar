package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.crypto.KeyParser;
import org.briarproject.api.crypto.PrivateKey;
import org.briarproject.api.crypto.Signature;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.forum.ForumPost;
import org.briarproject.api.forum.ForumPostFactory;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.util.StringUtils;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.forum.ForumConstants.MAX_CONTENT_TYPE_LENGTH;
import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;

class ForumPostFactoryImpl implements ForumPostFactory {

	private final CryptoComponent crypto;
	private final ClientHelper clientHelper;

	@Inject
	ForumPostFactoryImpl(CryptoComponent crypto, ClientHelper clientHelper) {
		this.crypto = crypto;
		this.clientHelper = clientHelper;
	}

	@Override
	public ForumPost createAnonymousPost(GroupId groupId, long timestamp,
			MessageId parent, String contentType, byte[] body)
			throws FormatException {
		// Validate the arguments
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_FORUM_POST_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the message
		BdfList message = BdfList.of(parent, null, contentType, body, null);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new ForumPost(m, parent, null);
	}

	@Override
	public ForumPost createPseudonymousPost(GroupId groupId, long timestamp,
			MessageId parent, LocalAuthor author, String bodyStr)
			throws FormatException, GeneralSecurityException {
		String contentType = "text/plain";
		byte[] body = StringUtils.toUtf8(bodyStr);
		// Validate the arguments
		if (StringUtils.toUtf8(contentType).length > MAX_CONTENT_TYPE_LENGTH)
			throw new IllegalArgumentException();
		if (body.length > MAX_FORUM_POST_BODY_LENGTH)
			throw new IllegalArgumentException();
		// Serialise the data to be signed
		BdfList authorList = BdfList.of(author.getName(),
				author.getPublicKey());
		BdfList signed = BdfList.of(groupId, timestamp, parent, authorList,
				contentType, body);
		// Get private key
		KeyParser keyParser = crypto.getSignatureKeyParser();
		byte[] k = author.getPrivateKey();
		PrivateKey privateKey = keyParser.parsePrivateKey(k);
		// Generate the signature
		Signature signature = crypto.getSignature();
		signature.initSign(privateKey);
		signature.update(clientHelper.toByteArray(signed));
		byte[] sig = signature.sign();
		// Serialise the signed message
		BdfList message = BdfList.of(parent, authorList, contentType, body,
				sig);
		Message m = clientHelper.createMessage(groupId, timestamp, message);
		return new ForumPost(m, parent, author);
	}
}
