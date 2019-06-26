package org.briarproject.briar.android.attachment;

import android.support.annotation.Nullable;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;

import java.io.InputStream;
import java.util.List;

@NotNullByDefault
public interface AttachmentRetriever {

	void cachePut(MessageId messageId, List<AttachmentItem> attachments);

	@Nullable
	List<AttachmentItem> cacheGet(MessageId messageId);

	Attachment getMessageAttachment(AttachmentHeader h) throws DbException;

	/**
	 * Creates an {@link AttachmentItem} from the {@link Attachment}'s
	 * {@link InputStream} which will be closed when this method returns.
	 */
	AttachmentItem getAttachmentItem(Attachment a, boolean needsSize);
}
