package org.briarproject.briar.api.media;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

/**
 * An exception that is thrown when an {@link AttachmentHeader} is used to
 * load an {@link Attachment}, and the header refers to a message that is not
 * an attachment, or to an attachment that does not have the expected content
 * type.
 */
@NotNullByDefault
public class InvalidAttachmentException extends DbException {
	public InvalidAttachmentException() {
		super();
	}

	public InvalidAttachmentException(Throwable t) {
		super(t);
	}
}
