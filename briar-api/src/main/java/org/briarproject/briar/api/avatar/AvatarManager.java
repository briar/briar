package org.briarproject.briar.api.avatar;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.briar.api.media.Attachment;
import org.briarproject.briar.api.media.AttachmentHeader;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nullable;

@NotNullByDefault
public interface AvatarManager {

	/**
	 * The unique ID of the avatar client.
	 */
	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.avatar");

	/**
	 * The current major version of the avatar client.
	 */
	int MAJOR_VERSION = 0;

	/**
	 * The current minor version of the avatar client.
	 */
	int MINOR_VERSION = 0;

	/**
	 * Store a new profile image represented by the given InputStream
	 * and share it with all contacts.
	 */
	AttachmentHeader addAvatar(String contentType, InputStream in)
			throws DbException, IOException;

	/**
	 * Returns the current known profile image header for the given contact
	 * or null if none is known.
	 */
	@Nullable
	AttachmentHeader getAvatarHeader(Contact c) throws DbException;

	/**
	 * Returns our current profile image header or null if none has been added.
	 */
	@Nullable
	AttachmentHeader getMyAvatarHeader() throws DbException;

	/**
	 * Returns the profile image attachment for the given header.
	 */
	Attachment getAvatar(AttachmentHeader h) throws DbException;
}
