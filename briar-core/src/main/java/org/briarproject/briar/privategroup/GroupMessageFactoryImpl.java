package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;

import java.security.GeneralSecurityException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;

@Immutable
@NotNullByDefault
class GroupMessageFactoryImpl implements GroupMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	GroupMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor creator) {

		return createJoinMessage(groupId, timestamp, creator, null);
	}

	@Override
	public GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, byte[] creatorSignature) {

		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		return createJoinMessage(groupId, timestamp, member, invite);
	}

	private GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, @Nullable BdfList invite) {
		try {
			// Generate the signature
			int type = JOIN.getInt();
			BdfList toSign = BdfList.of(groupId, timestamp, type,
					member.getName(), member.getPublicKey(), invite);
			byte[] memberSignature = clientHelper
					.sign(SIGNING_LABEL_JOIN, toSign, member.getPrivateKey());

			// Compose the message
			BdfList body =
					BdfList.of(type, member.getName(),
							member.getPublicKey(), invite, memberSignature);
			Message m = clientHelper.createMessage(groupId, timestamp, body);

			return new GroupMessage(m, null, member);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author, String content,
			MessageId previousMsgId) {
		try {
			// Generate the signature
			int type = POST.getInt();
			BdfList toSign = BdfList.of(groupId, timestamp, type,
					author.getName(), author.getPublicKey(), parentId,
					previousMsgId, content);
			byte[] signature = clientHelper
					.sign(SIGNING_LABEL_POST, toSign, author.getPrivateKey());

			// Compose the message
			BdfList body =
					BdfList.of(type, author.getName(),
							author.getPublicKey(), parentId, previousMsgId,
							content, signature);
			Message m = clientHelper.createMessage(groupId, timestamp, body);

			return new GroupMessage(m, parentId, author);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

}
