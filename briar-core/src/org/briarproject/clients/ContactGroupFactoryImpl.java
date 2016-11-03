package org.briarproject.clients;


import org.briarproject.api.Bytes;
import org.briarproject.api.FormatException;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.clients.ContactGroupFactory;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;

import javax.inject.Inject;

class ContactGroupFactoryImpl implements ContactGroupFactory {

	private static final byte[] LOCAL_GROUP_DESCRIPTOR = new byte[0];

	private final GroupFactory groupFactory;
	private final ClientHelper clientHelper;

	@Inject
	ContactGroupFactoryImpl(GroupFactory groupFactory,
			ClientHelper clientHelper) {
		this.groupFactory = groupFactory;
		this.clientHelper = clientHelper;
	}

	@Override
	public Group createLocalGroup(ClientId clientId) {
		return groupFactory.createGroup(clientId, LOCAL_GROUP_DESCRIPTOR);
	}

	@Override
	public Group createContactGroup(ClientId clientId, Contact contact) {
		AuthorId local = contact.getLocalAuthorId();
		AuthorId remote = contact.getAuthor().getId();
		byte[] descriptor = createGroupDescriptor(local, remote);
		return groupFactory.createGroup(clientId, descriptor);
	}

	@Override
	public Group createContactGroup(ClientId clientId, AuthorId authorId1,
			AuthorId authorId2) {
		byte[] descriptor = createGroupDescriptor(authorId1, authorId2);
		return groupFactory.createGroup(clientId, descriptor);
	}

	private byte[] createGroupDescriptor(AuthorId local, AuthorId remote) {
		try {
			if (Bytes.COMPARATOR.compare(local, remote) < 0)
				return clientHelper.toByteArray(BdfList.of(local, remote));
			else return clientHelper.toByteArray(BdfList.of(remote, local));
		} catch (FormatException e) {
			throw new RuntimeException(e);
		}
	}
}
