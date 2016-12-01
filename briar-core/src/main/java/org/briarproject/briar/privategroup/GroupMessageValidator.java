package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorFactory;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_JOIN;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_POST;
import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_BODY_LENGTH;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INITIAL_JOIN_MSG;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_NAME;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER_PUBLIC_KEY;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_READ;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TYPE;

@Immutable
@NotNullByDefault
class GroupMessageValidator extends BdfMessageValidator {

	private final PrivateGroupFactory privateGroupFactory;
	private final AuthorFactory authorFactory;
	private final GroupInvitationFactory groupInvitationFactory;

	GroupMessageValidator(PrivateGroupFactory privateGroupFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, AuthorFactory authorFactory,
			GroupInvitationFactory groupInvitationFactory) {
		super(clientHelper, metadataEncoder, clock);
		this.privateGroupFactory = privateGroupFactory;
		this.authorFactory = authorFactory;
		this.groupInvitationFactory = groupInvitationFactory;
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
		if (type == JOIN.getInt()) {
			c = validateJoin(m, g, body, member);
			addMessageMetadata(c, member, m.getTimestamp());
		} else if (type == POST.getInt()) {
			c = validatePost(m, g, body, member);
			addMessageMetadata(c, member, m.getTimestamp());
		} else {
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
		PrivateGroup pg = privateGroupFactory.parsePrivateGroup(g);

		// invite is null if the member is the creator of the private group
		Author creator = pg.getCreator();
		boolean isCreator = false;
		BdfList invite = body.getOptionalList(3);
		if (invite == null) {
			if (!member.equals(creator))
				throw new InvalidMessageException();
			isCreator = true;
		} else {
			if (member.equals(creator))
				throw new InvalidMessageException();

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

			// the invite token is signed by the creator of the private group
			BdfList token = groupInvitationFactory
					.createInviteToken(creator.getId(), member.getId(),
							pg.getId(), inviteTimestamp);
			try {
				clientHelper
						.verifySignature(SIGNING_LABEL_INVITE, creatorSignature,
								creator.getPublicKey(), token);
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
			clientHelper.verifySignature(SIGNING_LABEL_JOIN, memberSignature,
					member.getPublicKey(), signed);
		} catch (GeneralSecurityException e) {
			throw new InvalidMessageException(e);
		}

		// Return the metadata and no dependencies
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_INITIAL_JOIN_MSG, isCreator);
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validatePost(Message m, Group g, BdfList body,
			Author member)
			throws InvalidMessageException, FormatException {

		// The content is a BDF list with seven elements
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
		checkLength(content, 1, MAX_GROUP_POST_BODY_LENGTH);

		// signature (raw)
		// a signature with the member's private key over a list with 7 elements
		byte[] signature = body.getRaw(6);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Verify Signature
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), POST.getInt(),
				member.getName(), member.getPublicKey(), parentId,
				previousMessageId, content);
		try {
			clientHelper.verifySignature(SIGNING_LABEL_POST, signature,
					member.getPublicKey(), signed);
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
