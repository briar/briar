package org.briarproject.briar.api.attachment;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchMessageException;

public interface AttachmentReader {

	/**
	 * Returns the attachment with the given attachment header.
	 *
	 * @throws NoSuchMessageException If the header refers to a message in
	 * a different group from the one specified in the header, to a message
	 * that is not an attachment, or to an attachment that does not have the
	 * expected content type. This is meant to prevent social engineering
	 * attacks that use invalid attachment IDs to test whether messages exist
	 * in the victim's database
	 */
	Attachment getAttachment(AttachmentHeader h) throws DbException;

}
