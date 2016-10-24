package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.privategroup.MessageType;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;
import static org.briarproject.privategroup.Constants.KEY_AUTHOR_ID;
import static org.briarproject.privategroup.Constants.KEY_AUTHOR_NAME;
import static org.briarproject.privategroup.Constants.KEY_AUTHOR_PUBLIC_KEY;
import static org.briarproject.privategroup.Constants.KEY_NEW_MEMBER_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_PARENT_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_READ;
import static org.briarproject.privategroup.Constants.KEY_TIMESTAMP;
import static org.briarproject.privategroup.Constants.KEY_TYPE;

class GroupMessageValidator extends BdfMessageValidator {

	private final PrivateGroupFactory groupFactory;
	private final AuthorFactory authorFactory;

	GroupMessageValidator(PrivateGroupFactory groupFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, AuthorFactory authorFactory) {
		super(clientHelper, metadataEncoder, clock);
		this.groupFactory = groupFactory;
		this.authorFactory = authorFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		checkSize(body, 4, 7);

		// message type (int)
		int type = body.getLong(0).intValue();
		body.removeElementAt(0);

		// member_name (string)
		String member_name = body.getString(0);
		checkLength(member_name, 1, MAX_AUTHOR_NAME_LENGTH);

		// member_public_key (raw)
		byte[] member_public_key = body.getRaw(1);
		checkLength(member_public_key, 1, MAX_PUBLIC_KEY_LENGTH);

		BdfMessageContext c;
		switch (MessageType.valueOf(type)) {
			case NEW_MEMBER:
				c = validateNewMember(m, g, body, member_name,
						member_public_key);
				addMessageMetadata(c, member_name, member_public_key,
						m.getTimestamp());
				break;
			case JOIN:
				c = validateJoin(m, g, body, member_name, member_public_key);
				addMessageMetadata(c, member_name, member_public_key,
						m.getTimestamp());
				break;
			case POST:
				c = validatePost(m, g, body, member_name, member_public_key);
				addMessageMetadata(c, member_name, member_public_key,
						m.getTimestamp());
				break;
			default:
				throw new InvalidMessageException("Unknown Message Type");
		}
		c.getDictionary().put(KEY_TYPE, type);
		return c;
	}

	private BdfMessageContext validateNewMember(Message m, Group g,
			BdfList body, String member_name, byte[] member_public_key)
			throws InvalidMessageException, FormatException {

		// The content is a BDF list with three elements
		checkSize(body, 3);

		// signature (raw)
		// signature with the creator's private key over a list with 4 elements
		byte[] signature = body.getRaw(2);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Verify Signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), member_name,
				member_public_key);
		PrivateGroup group = groupFactory.parsePrivateGroup(g);
		byte[] creatorPublicKey = group.getAuthor().getPublicKey();
		clientHelper.verifySignature(signature, creatorPublicKey, signed);

		// Return the metadata and no dependencies
		BdfDictionary meta = new BdfDictionary();
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validateJoin(Message m, Group g, BdfList body,
			String member_name, byte[] member_public_key)
			throws InvalidMessageException, FormatException {

		// The content is a BDF list with four elements
		checkSize(body, 4);

		// new_member_id (raw)
		// the identifier of a new member message
		// with the same member_name and member_public_key
		byte[] new_member_id = body.getRaw(2);
		checkLength(new_member_id, MessageId.LENGTH);

		// signature (raw)
		// a signature with the member's private key over a list with 5 elements
		byte[] signature = body.getRaw(3);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Verify Signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), member_name,
				member_public_key, new_member_id);
		clientHelper.verifySignature(signature, member_public_key, signed);

		// The new member message is a dependency
		Collection<MessageId> dependencies =
				Collections.singleton(new MessageId(new_member_id));

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_NEW_MEMBER_MSG_ID, new_member_id);
		return new BdfMessageContext(meta, dependencies);
	}

	private BdfMessageContext validatePost(Message m, Group g, BdfList body,
			String member_name, byte[] member_public_key)
			throws InvalidMessageException, FormatException {

		// The content is a BDF list with six elements
		checkSize(body, 6);

		// parent_id (raw or null)
		// the identifier of the post to which this is a reply, if any
		byte[] parent_id = body.getOptionalRaw(2);
		if (parent_id != null) {
			checkLength(parent_id, MessageId.LENGTH);
		}

		// previous_message_id (raw)
		// the identifier of the member's previous post or join message
		byte[] previous_message_id = body.getRaw(3);
		checkLength(previous_message_id, MessageId.LENGTH);

		// content (string)
		String content = body.getString(4);
		checkLength(content, 0, MAX_GROUP_POST_BODY_LENGTH);

		// signature (raw)
		// a signature with the member's private key over a list with 7 elements
		byte[] signature = body.getRaw(5);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Verify Signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), member_name,
				member_public_key, parent_id, previous_message_id, content);
		clientHelper.verifySignature(signature, member_public_key, signed);

		// The parent post, if any,
		// and the member's previous message are dependencies
		Collection<MessageId> dependencies = new ArrayList<MessageId>();
		if (parent_id != null) dependencies.add(new MessageId(parent_id));
		dependencies.add(new MessageId(previous_message_id));

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		if (parent_id != null) meta.put(KEY_PARENT_MSG_ID, parent_id);
		meta.put(KEY_PREVIOUS_MSG_ID, previous_message_id);
		return new BdfMessageContext(meta, dependencies);
	}

	private void addMessageMetadata(BdfMessageContext c, String authorName,
			byte[] pubKey, long time) {
		c.getDictionary().put(KEY_TIMESTAMP, time);
		c.getDictionary().put(KEY_READ, false);
		Author a = authorFactory.createAuthor(authorName, pubKey);
		c.getDictionary().put(KEY_AUTHOR_ID, a.getId());
		c.getDictionary().put(KEY_AUTHOR_NAME, authorName);
		c.getDictionary().put(KEY_AUTHOR_PUBLIC_KEY, pubKey);
	}

}
