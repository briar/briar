package org.briarproject.briar.android.attachment;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.api.messaging.MessagingConstants.IMAGE_MIME_TYPES;

@NotNullByDefault
class AttachmentCreationTask {

	private static Logger LOG =
			getLogger(AttachmentCreationTask.class.getName());

	private final MessagingManager messagingManager;
	private final ContentResolver contentResolver;
	private final GroupId groupId;
	private final List<Uri> uris;
	private final boolean needsSize;
	@Nullable
	private AttachmentCreator attachmentCreator;

	private volatile boolean canceled = false;

	AttachmentCreationTask(MessagingManager messagingManager,
			ContentResolver contentResolver,
			AttachmentCreator attachmentCreator, GroupId groupId,
			List<Uri> uris, boolean needsSize) {
		this.messagingManager = messagingManager;
		this.contentResolver = contentResolver;
		this.groupId = groupId;
		this.uris = uris;
		this.needsSize = needsSize;
		this.attachmentCreator = attachmentCreator;
	}

	public void cancel() {
		canceled = true;
		attachmentCreator = null;
	}

	@IoExecutor
	public void storeAttachments() {
		for (Uri uri: uris) processUri(uri);
		if (!canceled && attachmentCreator != null)
			attachmentCreator.onAttachmentCreationFinished();
		attachmentCreator = null;
	}

	@IoExecutor
	private void processUri(Uri uri) {
		if (canceled) return;
		try {
			AttachmentHeader h = storeAttachment(uri);
			if (attachmentCreator != null) {
				attachmentCreator
						.onAttachmentHeaderReceived(uri, h, needsSize);
			}
		} catch (DbException | IOException e) {
			logException(LOG, WARNING, e);
			if (attachmentCreator != null) {
				attachmentCreator.onAttachmentError(uri, e);
				canceled = true;
			}
		}
	}

	@IoExecutor
	private AttachmentHeader storeAttachment(Uri uri)
			throws IOException, DbException {
		long start = now();
		String contentType = contentResolver.getType(uri);
		if (contentType == null) throw new IOException("null content type");
		if (!isValidMimeType(contentType))
			throw new UnsupportedMimeTypeException("", contentType,
					uri.toString());
		InputStream is = contentResolver.openInputStream(uri);
		if (is == null) throw new IOException();
		long timestamp = System.currentTimeMillis();
		AttachmentHeader h = messagingManager
				.addLocalAttachment(groupId, timestamp, contentType, is);
		tryToClose(is, LOG, WARNING);
		logDuration(LOG, "Storing attachment", start);
		return h;
	}

	private boolean isValidMimeType(@Nullable String mimeType) {
		if (mimeType == null) return false;
		for (String supportedType : IMAGE_MIME_TYPES) {
			if (supportedType.equals(mimeType)) return true;
		}
		return false;
	}

}
