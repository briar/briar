package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.Nullable;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

import static org.briarproject.api.privategroup.MessageType.JOIN;
import static org.briarproject.api.privategroup.MessageType.NEW_MEMBER;
import static org.briarproject.api.privategroup.MessageType.POST;

@NotNullByDefault
class GroupMessageFactoryImpl implements GroupMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	GroupMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public GroupMessage createNewMemberMessage(GroupId groupId, long timestamp,
			LocalAuthor creator, Author member) {
		try {
			// Generate the signature
			int type = NEW_MEMBER.getInt();
			BdfList toSign = BdfList.of(groupId, timestamp, type,
					member.getName(), member.getPublicKey());
			byte[] signature =
					clientHelper.sign(toSign, creator.getPrivateKey());

			// Compose the message
			BdfList body =
					BdfList.of(type, member.getName(),
							member.getPublicKey(), signature);
			Message m = clientHelper.createMessage(groupId, timestamp, body);

			return new GroupMessage(m, null, member);
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, MessageId newMemberId) {
		try {
			// Generate the signature
			int type = JOIN.getInt();
			BdfList toSign = BdfList.of(groupId, timestamp, type,
					member.getName(), member.getPublicKey(), newMemberId);
			byte[] signature =
					clientHelper.sign(toSign, member.getPrivateKey());

			// Compose the message
			BdfList body =
					BdfList.of(type, member.getName(),
							member.getPublicKey(), newMemberId, signature);
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
			byte[] signature =
					clientHelper.sign(toSign, author.getPrivateKey());

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
