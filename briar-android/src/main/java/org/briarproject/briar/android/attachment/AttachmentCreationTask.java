package org.briarproject.briar.android.attachment;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.ContentResolver;
import android.net.Uri;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.api.messaging.MessagingConstants.IMAGE_MIME_TYPES;

@ThreadSafe
@NotNullByDefault
class AttachmentCreationTask {

	private static Logger LOG =
			getLogger(AttachmentCreationTask.class.getName());

	private final Executor ioExecutor;
	private final MessagingManager messagingManager;
	private final ContentResolver contentResolver;
	private final AttachmentRetriever retriever;
	private final GroupId groupId;
	private final Collection<Uri> uris;
	private final boolean needsSize;
	private final MutableLiveData<AttachmentResult> result;

	private volatile boolean canceled = false;

	AttachmentCreationTask(Executor ioExecutor,
			MessagingManager messagingManager, ContentResolver contentResolver,
			AttachmentRetriever retriever, GroupId groupId,
			Collection<Uri> uris, boolean needsSize) {
		this.ioExecutor = ioExecutor;
		this.messagingManager = messagingManager;
		this.contentResolver = contentResolver;
		this.retriever = retriever;
		this.groupId = groupId;
		this.uris = uris;
		this.needsSize = needsSize;
		result = new MutableLiveData<>();
	}

	LiveData<AttachmentResult> getResult() {
		return result;
	}

	/**
	 * Cancels the task, asynchronously waits for it to finish, and deletes any
	 * created attachments.
	 */
	void cancel() {
		canceled = true;
		// Observe the task until it finishes (which may already have happened)
		result.observeForever(new Observer<AttachmentResult>() {
			@Override
			public void onChanged(@Nullable AttachmentResult attachmentResult) {
				requireNonNull(attachmentResult);
				if (attachmentResult.isFinished()) {
					deleteUnsentAttachments(attachmentResult.getItemResults());
					result.removeObserver(this);
				}
			}
		});
	}

	/**
	 * Asynchronously creates and stores the attachments.
	 */
	void storeAttachments() {
		ioExecutor.execute(() -> {
			if (LOG.isLoggable(INFO))
				LOG.info("Storing " + uris.size() + " attachments");
			List<AttachmentItemResult> results = new ArrayList<>();
			for (Uri uri : uris) {
				if (canceled) break;
				results.add(processUri(uri));
				result.postValue(new AttachmentResult(new ArrayList<>(results),
						false, false));
			}
			result.postValue(new AttachmentResult(new ArrayList<>(results),
					true, !canceled));
		});
	}

	@IoExecutor
	private AttachmentItemResult processUri(Uri uri) {
		AttachmentHeader header = null;
		try {
			header = storeAttachment(uri);
			Attachment a = retriever.getMessageAttachment(header);
			AttachmentItem item =
					retriever.getAttachmentItem(header, a, needsSize);
			if (item.hasError()) throw new IOException();
			retriever.cachePut(item);
			return new AttachmentItemResult(uri, item);
		} catch (DbException | IOException e) {
			logException(LOG, WARNING, e);
			// If the attachment was already stored, delete it
			tryToRemove(header);
			canceled = true;
			return new AttachmentItemResult(uri, e);
		}
	}

	@IoExecutor
	private AttachmentHeader storeAttachment(Uri uri)
			throws IOException, DbException {
		long start = now();
		String contentType = contentResolver.getType(uri);
		if (contentType == null) throw new IOException("null content type");
		if (!isValidMimeType(contentType)) {
			String uriString = uri.toString();
			throw new UnsupportedMimeTypeException("", contentType, uriString);
		}
		InputStream is = contentResolver.openInputStream(uri);
		if (is == null) throw new IOException();
		long timestamp = System.currentTimeMillis();
		AttachmentHeader h = messagingManager
				.addLocalAttachment(groupId, timestamp, contentType, is);
		tryToClose(is, LOG, WARNING);
		logDuration(LOG, "Storing attachment", start);
		return h;
	}

	private boolean isValidMimeType(String mimeType) {
		for (String supportedType : IMAGE_MIME_TYPES) {
			if (supportedType.equals(mimeType)) return true;
		}
		return false;
	}

	private void tryToRemove(@Nullable AttachmentHeader h) {
		try {
			if (h != null) messagingManager.removeAttachment(h);
		} catch (DbException e1) {
			logException(LOG, WARNING, e1);
		}
	}

	private void deleteUnsentAttachments(
			Collection<AttachmentItemResult> itemResults) {
		List<AttachmentHeader> headers = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			AttachmentItem item = itemResult.getItem();
			if (item != null) headers.add(item.getHeader());
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Deleting " + headers.size() + " unsent attachments");
		ioExecutor.execute(() -> {
			for (AttachmentHeader header : headers) {
				try {
					messagingManager.removeAttachment(header);
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
			}
		});
	}
}
