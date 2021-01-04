package org.briarproject.briar.api.attachment;

import org.briarproject.bramble.api.db.DbException;

public interface AttachmentReader {

	/**
	 * Returns the attachment with the given attachment header.
	 *
	 * @throws InvalidAttachmentException If the header refers to a message
	 * that is not an attachment, or to an attachment that does not have the
	 * expected content type
	 */
	Attachment getAttachment(AttachmentHeader h) throws DbException;

}
