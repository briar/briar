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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.util.UiUtils.observeForeverOnce;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_IMAGE_SIZE;

@NotNullByDefault
public class AttachmentCreator {

	private static Logger LOG = getLogger(AttachmentCreator.class.getName());

	private final Application app;
	@IoExecutor
	private final Executor ioExecutor;
	private final MessagingManager messagingManager;
	private final AttachmentRetriever retriever;

	private final CopyOnWriteArrayList<Uri> uris = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<AttachmentItemResult> itemResults =
			new CopyOnWriteArrayList<>();

	@Nullable
	private volatile MutableLiveData<AttachmentResult> result = null;
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
	public LiveData<AttachmentResult> storeAttachments(
			LiveData<GroupId> groupId, Collection<Uri> newUris, boolean restart) {
		MutableLiveData<AttachmentResult> result;
		if (restart) {
			// This can happen due to configuration changes.
			// So don't create new attachments. They are already being created
			// and returned by the existing LiveData.
			result = this.result;
			if (task == null || uris.isEmpty() || result == null)
				throw new IllegalStateException();
			// A task is already running. It will update the result LiveData.
			// So nothing more to do here.
		} else {
			if (this.result != null || !uris.isEmpty())
				throw new IllegalStateException();
			result = new MutableLiveData<>();
			this.result = result;
			uris.addAll(newUris);
			observeForeverOnce(groupId, id -> {
				if (id == null) throw new IllegalStateException();
				boolean needsSize = uris.size() == 1;
				task = new AttachmentCreationTask(messagingManager,
						app.getContentResolver(), this, id, uris, needsSize);
				ioExecutor.execute(() -> task.storeAttachments());
			});
		}
		return result;
	}

	@IoExecutor
	void onAttachmentHeaderReceived(Uri uri, AttachmentHeader h,
			boolean needsSize) {
		MutableLiveData<AttachmentResult> result = this.result;
		if (result == null) return;
		// get and cache AttachmentItem for ImagePreview
		try {
			Attachment a = retriever.getMessageAttachment(h);
			AttachmentItem item = retriever.getAttachmentItem(h, a, needsSize);
			if (item.hasError()) throw new IOException();
			AttachmentItemResult itemResult =
					new AttachmentItemResult(uri, item);
			itemResults.add(itemResult);
			result.postValue(new AttachmentResult(itemResults, false));
		} catch (IOException | DbException e) {
			logException(LOG, WARNING, e);
			onAttachmentError(uri, e);
		}
	}

	@IoExecutor
	void onAttachmentError(Uri uri, Throwable t) {
		MutableLiveData<AttachmentResult> result = this.result;
		if (result == null) return;
		// get error message
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
		AttachmentItemResult itemResult =
				new AttachmentItemResult(uri, errorMsg);
		itemResults.add(itemResult);
		result.postValue(new AttachmentResult(itemResults, false));
		// expect to receive a cancel from the UI
	}

	@IoExecutor
	void onAttachmentCreationFinished() {
		if (uris.size() != itemResults.size())
			throw new IllegalStateException();
		MutableLiveData<AttachmentResult> result = this.result;
		if (result == null) return;
		result.postValue(new AttachmentResult(itemResults, true));
	}

	@UiThread
	public List<AttachmentHeader> getAttachmentHeadersForSending() {
		List<AttachmentHeader> headers = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			// check if we are trying to send attachment items with errors
			if (itemResult.getItem() == null) throw new IllegalStateException();
			headers.add(itemResult.getItem().getHeader());
		}
		return headers;
	}

	/**
	 * Marks the attachments as sent and adds the items to the cache for display
	 *
	 * @param id The MessageId of the sent message.
	 */
	public void onAttachmentsSent(MessageId id) {
		List<AttachmentItem> items = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			// check if we are trying to send attachment items with errors
			if (itemResult.getItem() == null) throw new IllegalStateException();
			items.add(itemResult.getItem());
		}
		retriever.cachePut(id, items);
		resetState();
	}

	/**
	 * Needs to be called when created attachments will not be sent anymore.
	 */
	@UiThread
	public void cancel() {
		if (task == null) throw new AssertionError();
		task.cancel();
		// let observers know that they can remove themselves
		MutableLiveData<AttachmentResult> result = this.result;
		if (result != null) {
			result.setValue(null);
		}
		deleteUnsentAttachments();
		resetState();
	}

	@UiThread
	private void resetState() {
		task = null;
		uris.clear();
		itemResults.clear();
		result = null;
	}

	@UiThread
	public void deleteUnsentAttachments() {
		// Make a copy for the IoExecutor as we clear the itemResults soon
		List<AttachmentHeader> headers = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			// check if we are trying to send attachment items with errors
			if (itemResult.getItem() != null)
				headers.add(itemResult.getItem().getHeader());
		}
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
