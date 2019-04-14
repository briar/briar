package org.briarproject.briar.android.attachment;


import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.FileTooBigException;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_IMAGE_SIZE;

@NotNullByDefault
public class AttachmentCreator {

	private static Logger LOG = getLogger(AttachmentCreator.class.getName());

	private final Application app;
	@IoExecutor
	private final Executor ioExecutor;
	private final MessagingManager messagingManager;
	private final AttachmentRetriever retriever;

	// store unsent items separately, as LiveData might not return latest value
	private final Map<Uri, AttachmentItem> unsentItems =
			new ConcurrentHashMap<>();
	private final Map<Uri, MutableLiveData<AttachmentItemResult>>
			liveDataResult = new ConcurrentHashMap<>();

	@Nullable
	private MutableLiveData<Boolean> liveDataFinished = null;
	@Nullable
	private AttachmentCreationTask task;

	public AttachmentCreator(Application app, @IoExecutor Executor ioExecutor,
			MessagingManager messagingManager, AttachmentRetriever retriever) {
		this.app = app;
		this.ioExecutor = ioExecutor;
		this.messagingManager = messagingManager;
		this.retriever = retriever;
	}

	@UiThread
	public AttachmentResult storeAttachments(GroupId groupId,
			Collection<Uri> uris, boolean restart) {
		List<LiveData<AttachmentItemResult>> itemResults = new ArrayList<>();
		if (restart) {
			// This can happen due to configuration changes.
			// So don't create new attachments, if we have (or creating) them.
			// Instead, re-subscribe to the existing LiveData.
			if (task == null || isNotStoring()) throw new AssertionError();
			for (Uri uri : uris) {
				// We don't want to expose mutable(!) LiveData
				LiveData<AttachmentItemResult> liveData =
						liveDataResult.get(uri);
				if (liveData == null) throw new IllegalStateException();
				itemResults.add(liveData);
			}
			if (liveDataFinished == null) throw new IllegalStateException();
		} else {
			if (task != null && isNotStoring()) throw new AssertionError();
			List<Uri> urisToStore = new ArrayList<>();
			for (Uri uri : uris) {
				urisToStore.add(uri);
				MutableLiveData<AttachmentItemResult> liveData =
						new MutableLiveData<>();
				liveDataResult.put(uri, liveData);
				itemResults.add(liveData);
			}
			boolean needsSize = uris.size() == 1;
			task = new AttachmentCreationTask(messagingManager,
					app.getContentResolver(), this, groupId, urisToStore,
					needsSize);
			ioExecutor.execute(() -> task.storeAttachments());
			liveDataFinished = new MutableLiveData<>();
		}
		return new AttachmentResult(itemResults, liveDataFinished);
	}

	@IoExecutor
	void onAttachmentHeaderReceived(Uri uri, AttachmentHeader h,
			boolean needsSize) {
		// get and cache AttachmentItem for ImagePreview
		try {
			Attachment a = retriever.getMessageAttachment(h);
			AttachmentItem item = retriever.getAttachmentItem(h, a, needsSize);
			if (item.hasError()) throw new IOException();
			unsentItems.put(uri, item);
			MutableLiveData<AttachmentItemResult> result =
					liveDataResult.get(uri);
			if (result != null) {  // might have been cleared on UiThread
				result.postValue(new AttachmentItemResult(uri, item));
			}
		} catch (IOException | DbException e) {
			logException(LOG, WARNING, e);
			onAttachmentError(uri, e);
		}
	}

	@IoExecutor
	void onAttachmentError(Uri uri, Throwable t) {
		String errorMsg;
		if (t instanceof UnsupportedMimeTypeException) {
			String mimeType = ((UnsupportedMimeTypeException) t).getMimeType();
			errorMsg = app.getString(
					R.string.image_attach_error_invalid_mime_type, mimeType);
		} else if (t instanceof FileTooBigException) {
			int mb = MAX_IMAGE_SIZE / 1024 / 1024;
			errorMsg = app.getString(R.string.image_attach_error_too_big, mb);
		} else {
			errorMsg = null; // generic error
		}
		MutableLiveData<AttachmentItemResult> result = liveDataResult.get(uri);
		if (result != null)
			result.postValue(new AttachmentItemResult(errorMsg));
		// expect to receive a cancel from the UI
	}

	@IoExecutor
	void onAttachmentCreationFinished() {
		if (liveDataFinished != null) liveDataFinished.postValue(true);
	}

	@UiThread
	public List<AttachmentHeader> getAttachmentHeadersForSending() {
		List<AttachmentHeader> headers =
				new ArrayList<>(unsentItems.values().size());
		for (AttachmentItem item : unsentItems.values()) {
			headers.add(item.getHeader());
		}
		return headers;
	}

	/**
	 * Marks the attachments as sent and adds the items to the cache for display
	 *
	 * @param id The MessageId of the sent message.
	 */
	public void onAttachmentsSent(MessageId id) {
		retriever.cachePut(id, new ArrayList<>(unsentItems.values()));
		resetState();
	}

	@UiThread
	public void cancel() {
		if (task == null) throw new AssertionError();
		task.cancel();
		// let observers know that they can remove themselves
		for (MutableLiveData<AttachmentItemResult> liveData : liveDataResult
				.values()) {
			if (liveData.getValue() == null) {
				liveData.setValue(null);
			}
		}
		if (liveDataFinished != null) liveDataFinished.setValue(false);
		deleteUnsentAttachments();
		resetState();
	}

	@UiThread
	private void resetState() {
		task = null;
		liveDataResult.clear();
		liveDataFinished = null;
		unsentItems.clear();
	}

	@UiThread
	public void deleteUnsentAttachments() {
		// Make a copy for the IoExecutor as we clear the unsentItems soon
		List<AttachmentItem> itemsToDelete =
				new ArrayList<>(unsentItems.values());
		ioExecutor.execute(() -> {
			for (AttachmentItem item : itemsToDelete) {
				try {
					messagingManager.removeAttachment(item.getHeader());
				} catch (DbException e) {
					logException(LOG, WARNING, e);
				}
			}
		});
	}

	private boolean isNotStoring() {
		return liveDataFinished == null;
	}

}
