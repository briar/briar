package org.briarproject.forum;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.briarproject.api.forum.ForumPostFactory.SIGNING_LABEL_POST;
import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;

@NotNullByDefault
class ForumPostValidator extends BdfMessageValidator {

	private final AuthorFactory authorFactory;

	ForumPostValidator(AuthorFactory authorFactory, ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
		this.authorFactory = authorFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {
		// Parent ID, author, content type, forum post body, signature
		checkSize(body, 4);

		// Parent ID is optional
		byte[] parent = body.getOptionalRaw(0);
		checkLength(parent, UniqueId.LENGTH);

		// Author
		BdfList authorList = body.getList(1);
		// Name, public key
		checkSize(authorList, 2);
		String name = authorList.getString(0);
		checkLength(name, 1, MAX_AUTHOR_NAME_LENGTH);
		byte[] publicKey = authorList.getRaw(1);
		checkLength(publicKey, 0, MAX_PUBLIC_KEY_LENGTH);
		Author author = authorFactory.createAuthor(name, publicKey);

		// Forum post body
		String forumPostBody = body.getString(2);
		checkLength(forumPostBody, 0, MAX_FORUM_POST_BODY_LENGTH);

		// Signature
		byte[] sig = body.getRaw(3);
		checkLength(sig, 0, MAX_SIGNATURE_LENGTH);
		// Verify the signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), parent,
				authorList, forumPostBody);
		try {
			clientHelper.verifySignature(SIGNING_LABEL_POST, sig,
					author.getPublicKey(), signed);
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		Collection<MessageId> dependencies = Collections.emptyList();
		meta.put("timestamp", m.getTimestamp());
		if (parent != null) {
			meta.put("parent", parent);
			dependencies = Collections.singletonList(new MessageId(parent));
		}
		BdfDictionary authorMeta = new BdfDictionary();
		authorMeta.put("id", author.getId());
		authorMeta.put("name", author.getName());
		authorMeta.put("publicKey", author.getPublicKey());
		meta.put("author", authorMeta);
		meta.put("read", false);
		return new BdfMessageContext(meta, dependencies);
	}
}
