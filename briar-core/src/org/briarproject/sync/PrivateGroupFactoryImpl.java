package org.briarproject.sync;

import com.google.inject.Inject;

import org.briarproject.api.UniqueId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.ClientId;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.GroupFactory;
import org.briarproject.api.sync.PrivateGroupFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class PrivateGroupFactoryImpl implements PrivateGroupFactory {

	private final GroupFactory groupFactory;
	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	PrivateGroupFactoryImpl(GroupFactory groupFactory,
			BdfWriterFactory bdfWriterFactory) {
		this.groupFactory = groupFactory;
		this.bdfWriterFactory = bdfWriterFactory;
	}

	@Override
	public Group createPrivateGroup(ClientId clientId, Contact contact) {
		AuthorId local = contact.getLocalAuthorId();
		AuthorId remote = contact.getAuthor().getId();
		byte[] descriptor = createGroupDescriptor(local, remote);
		return groupFactory.createGroup(clientId, descriptor);
	}

	private byte[] createGroupDescriptor(AuthorId local, AuthorId remote) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart();
			if (UniqueId.IdComparator.INSTANCE.compare(local, remote) < 0) {
				w.writeRaw(local.getBytes());
				w.writeRaw(remote.getBytes());
			} else {
				w.writeRaw(remote.getBytes());
				w.writeRaw(local.getBytes());
			}
			w.writeListEnd();
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}
}
