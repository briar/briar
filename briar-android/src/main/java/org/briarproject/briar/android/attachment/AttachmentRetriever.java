package org.briarproject.briar.android.attachment;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import java.io.InputStream;
import java.util.List;

@NotNullByDefault
public interface AttachmentRetriever {

	@DatabaseExecutor
	Attachment getMessageAttachment(AttachmentHeader h) throws DbException;

	List<AttachmentItem> getAttachmentItems(PrivateMessageHeader messageHeader);

	/**
	 * Retrieves item size and adds the item to the cache, if available.
	 */
	@DatabaseExecutor
	void cacheAttachmentItem(MessageId conversationMessageId,
			AttachmentHeader h) throws DbException;

	/**
	 * Creates an {@link AttachmentItem} from the {@link Attachment}'s
	 * {@link InputStream} which will be closed when this method returns.
	 */
	AttachmentItem createAttachmentItem(Attachment a, boolean needsSize);

	@DatabaseExecutor
	Pair<MessageId, AttachmentItem> loadAttachmentItem(MessageId attachmentId)
			throws DbException;

}
