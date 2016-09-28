package org.briarproject.privategroup;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.privategroup.GroupMessage;
import org.briarproject.api.privategroup.GroupMessageFactory;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.Message;
import org.briarproject.api.sync.MessageId;
import org.jetbrains.annotations.NotNull;

import java.security.GeneralSecurityException;

import javax.inject.Inject;

class GroupMessageFactoryImpl implements GroupMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	GroupMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@NotNull
	@Override
	public GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			MessageId parent, LocalAuthor author, String body)
			throws FormatException, GeneralSecurityException {

		// Generate the signature
		byte[] sig = clientHelper.sign(new BdfList(), author.getPrivateKey());

		// Compose the message
		Message m =
				clientHelper.createMessage(groupId, timestamp, new BdfList());

		return new GroupMessage(m, parent, author);
	}

}
