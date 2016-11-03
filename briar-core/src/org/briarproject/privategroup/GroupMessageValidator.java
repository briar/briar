package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.privategroup.MessageType;
import org.briarproject.api.privategroup.PrivateGroup;
import org.briarproject.api.privategroup.PrivateGroupFactory;
import org.briarproject.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.InvalidMessageException;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;

import static org.briarproject.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.api.privategroup.MessageType.JOIN;
import static org.briarproject.api.privategroup.MessageType.POST;
import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;
import static org.briarproject.privategroup.Constants.KEY_MEMBER_ID;
import static org.briarproject.privategroup.Constants.KEY_MEMBER_NAME;
import static org.briarproject.privategroup.Constants.KEY_MEMBER_PUBLIC_KEY;
import static org.briarproject.privategroup.Constants.KEY_PARENT_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.privategroup.Constants.KEY_READ;
import static org.briarproject.privategroup.Constants.KEY_TIMESTAMP;
import static org.briarproject.privategroup.Constants.KEY_TYPE;

class GroupMessageValidator extends BdfMessageValidator {

	private final ContactGroupFactory contactGroupFactory;
	private final PrivateGroupFactory groupFactory;
	private final AuthorFactory authorFactory;
	private final GroupInvitationManager groupInvitationManager; // TODO remove

	GroupMessageValidator(ContactGroupFactory contactGroupFactory,
			PrivateGroupFactory groupFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, AuthorFactory authorFactory,
			GroupInvitationManager groupInvitationManager) {
		super(clientHelper, metadataEncoder, clock);
		this.contactGroupFactory = contactGroupFactory;
		this.groupFactory = groupFactory;
		this.authorFactory = authorFactory;
		this.groupInvitationManager = groupInvitationManager;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		checkSize(body, 5, 7);

		// message type (int)
		int type = body.getLong(0).intValue();

		// member_name (string)
		String memberName = body.getString(1);
		checkLength(memberName, 1, MAX_AUTHOR_NAME_LENGTH);

		// member_public_key (raw)
		byte[] memberPublicKey = body.getRaw(2);
		checkLength(memberPublicKey, 1, MAX_PUBLIC_KEY_LENGTH);

		Author member = authorFactory.createAuthor(memberName, memberPublicKey);
		BdfMessageContext c;
		switch (MessageType.valueOf(type)) {
			case JOIN:
				c = validateJoin(m, g, body, member);
				addMessageMetadata(c, member, m.getTimestamp());
				break;
			case POST:
				c = validatePost(m, g, body, member);
				addMessageMetadata(c, member, m.getTimestamp());
				break;
			default:
				throw new InvalidMessageException("Unknown Message Type");
		}
		c.getDictionary().put(KEY_TYPE, type);
		return c;
	}

	private BdfMessageContext validateJoin(Message m, Group g, BdfList body,
			Author member)
			throws InvalidMessageException, FormatException {

		// The content is a BDF list with five elements
		checkSize(body, 5);
		PrivateGroup pg = groupFactory.parsePrivateGroup(g);

		// invite is null if the member is the creator of the private group
		BdfList invite = body.getList(3, null);
		if (invite == null) {
			if (!member.equals(pg.getAuthor()))
				throw new InvalidMessageException();
		} else {
			// Otherwise invite is a list with two elements
			checkSize(invite, 2);

			// invite_timestamp (int)
			// join_timestamp must be greater than invite_timestamp
			long inviteTimestamp = invite.getLong(0);
			if (m.getTimestamp() <= inviteTimestamp)
				throw new InvalidMessageException();

			// creator_signature (raw)
			byte[] creatorSignature = invite.getRaw(1);
			checkLength(creatorSignature, 1, MAX_SIGNATURE_LENGTH);

			// derive invitation group
			Group invitationGroup = contactGroupFactory
					.createContactGroup(groupInvitationManager.getClientId(),
							pg.getAuthor().getId(), member.getId());

			// signature with the creator's private key
			// over a list with four elements:
			// invite_type (int), invite_timestamp (int),
			// invitation_group_id (raw), and private_group_id (raw)
			BdfList signed =
					BdfList.of(0, inviteTimestamp, invitationGroup.getId(),
							g.getId());
			try {
				clientHelper.verifySignature(creatorSignature,
						pg.getAuthor().getPublicKey(), signed);
			} catch (GeneralSecurityException e) {
				throw new InvalidMessageException(e);
			}
		}

		// member_signature (raw)
		// a signature with the member's private key over a list with 6 elements
		byte[] memberSignature = body.getRaw(4);
		checkLength(memberSignature, 1, MAX_SIGNATURE_LENGTH);

		// Verify Signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), JOIN.getInt(),
				member.getName(), member.getPublicKey(), invite);
		try {
			clientHelper.verifySignature(memberSignature, member.getPublicKey(),
					signed);
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// Return the metadata and no dependencies
		BdfDictionary meta = new BdfDictionary();
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validatePost(Message m, Group g, BdfList body,
			Author member)
			throws InvalidMessageException, FormatException {

		// The content is a BDF list with six elements
		checkSize(body, 7);

		// parent_id (raw or null)
		// the identifier of the post to which this is a reply, if any
		byte[] parentId = body.getOptionalRaw(3);
		checkLength(parentId, MessageId.LENGTH);

		// previous_message_id (raw)
		// the identifier of the member's previous post or join message
		byte[] previousMessageId = body.getRaw(4);
		checkLength(previousMessageId, MessageId.LENGTH);

		// content (string)
		String content = body.getString(5);
		checkLength(content, 0, MAX_GROUP_POST_BODY_LENGTH);

		// signature (raw)
		// a signature with the member's private key over a list with 7 elements
		byte[] signature = body.getRaw(6);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Verify Signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), POST.getInt(),
				member.getName(), member.getPublicKey(), parentId,
				previousMessageId, content);
		try {
			clientHelper
					.verifySignature(signature, member.getPublicKey(), signed);
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// The parent post, if any,
		// and the member's previous message are dependencies
		Collection<MessageId> dependencies = new ArrayList<MessageId>();
		if (parentId != null) dependencies.add(new MessageId(parentId));
		dependencies.add(new MessageId(previousMessageId));

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		if (parentId != null) meta.put(KEY_PARENT_MSG_ID, parentId);
		meta.put(KEY_PREVIOUS_MSG_ID, previousMessageId);
		return new BdfMessageContext(meta, dependencies);
	}

	private void addMessageMetadata(BdfMessageContext c, Author member,
			long time) {
		c.getDictionary().put(KEY_TIMESTAMP, time);
		c.getDictionary().put(KEY_READ, false);
		c.getDictionary().put(KEY_MEMBER_ID, member.getId());
		c.getDictionary().put(KEY_MEMBER_NAME, member.getName());
		c.getDictionary().put(KEY_MEMBER_PUBLIC_KEY, member.getPublicKey());
	}

}
