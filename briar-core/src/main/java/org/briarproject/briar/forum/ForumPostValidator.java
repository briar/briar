package org.briarproject.briar.forum;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;

import java.security.GeneralSecurityException;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_AUTHOR;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_PARENT;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_READ;
import static org.briarproject.briar.api.forum.ForumConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_TEXT_LENGTH;
import static org.briarproject.briar.api.forum.ForumPostFactory.SIGNING_LABEL_POST;

@Immutable
@NotNullByDefault
class ForumPostValidator extends BdfMessageValidator {

	ForumPostValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {
		// Parent ID, author, text, signature
		checkSize(body, 4);

		// Parent ID is optional
		byte[] parent = body.getOptionalRaw(0);
		checkLength(parent, UniqueId.LENGTH);

		// Author
		BdfList authorList = body.getList(1);
		Author author = clientHelper.parseAndValidateAuthor(authorList);

		// Text
		String text = body.getString(2);
		checkLength(text, 0, MAX_FORUM_POST_TEXT_LENGTH);

		// Signature
		byte[] sig = body.getRaw(3);
		checkLength(sig, 1, MAX_SIGNATURE_LENGTH);

		// Verify the signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), parent,
				authorList, text);
		try {
			clientHelper.verifySignature(sig, SIGNING_LABEL_POST,
					signed, author.getPublicKey());
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		Collection<MessageId> dependencies = emptyList();
		meta.put(KEY_TIMESTAMP, m.getTimestamp());
		if (parent != null) {
			meta.put(KEY_PARENT, parent);
			dependencies = singletonList(new MessageId(parent));
		}
		meta.put(KEY_AUTHOR, authorList);
		meta.put(KEY_READ, false);
		return new BdfMessageContext(meta, dependencies);
	}
}
