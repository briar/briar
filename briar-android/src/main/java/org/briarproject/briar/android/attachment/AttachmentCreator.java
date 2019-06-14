package org.briarproject.briar.android.attachment;


import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class AttachmentCreator {

	private static Logger LOG = getLogger(AttachmentCreator.class.getName());

	private final Application app;
	@IoExecutor
	private final Executor ioExecutor;
	private final MessagingManager messagingManager;
	private final AttachmentRetriever retriever;

	@Nullable
	private AttachmentCreationTask task;

	public AttachmentCreator(Application app, @IoExecutor Executor ioExecutor,
			MessagingManager messagingManager, AttachmentRetriever retriever) {
		this.app = app;
		this.ioExecutor = ioExecutor;
		this.messagingManager = messagingManager;
		this.retriever = retriever;
	}

	/**
	 * Starts a background task to create attachments from the given URIs and
	 * returns a LiveData to monitor the progress of the task.
	 */
	@UiThread
	public LiveData<AttachmentResult> storeAttachments(GroupId groupId,
			Collection<Uri> uris) {
		if (task != null) throw new IllegalStateException();
		boolean needsSize = uris.size() == 1;
		task = new AttachmentCreationTask(messagingManager,
				app.getContentResolver(), retriever, groupId, uris, needsSize);
		ioExecutor.execute(() -> task.storeAttachments());
		return task.getResult();
	}

	/**
	 * This should be only called after configuration changes.
	 * In this case we should not create new attachments.
	 * They are already being created and returned by the existing LiveData.
	 */
	@UiThread
	public LiveData<AttachmentResult> getLiveAttachments() {
		if (task == null) throw new IllegalStateException();
		// A task is already running. It will update the result LiveData.
		// So nothing more to do here.
		return task.getResult();
	}

	/**
	 * Returns the headers of any attachments created by
	 * {@link #storeAttachments(GroupId, Collection)}.
	 */
	@UiThread
	public List<AttachmentHeader> getAttachmentHeadersForSending() {
		if (task == null) return emptyList();
		AttachmentResult result = task.getResult().getValue();
		if (result == null) return emptyList();
		List<AttachmentHeader> headers = new ArrayList<>();
		for (AttachmentItemResult itemResult : result.getItemResults()) {
			AttachmentItem item = itemResult.getItem();
			if (item != null) headers.add(item.getHeader());
		}
		return headers;
	}

	/**
	 * Informs the AttachmentCreator that the attachments created by
	 * {@link #storeAttachments(GroupId, Collection)} will be sent.
	 */
	@UiThread
	public void onAttachmentsSent() {
		task = null;
	}

	/**
	 * Cancels the task started by
	 * {@link #storeAttachments(GroupId, Collection)} and deletes any
	 * created attachments, unless {@link #onAttachmentsSent()} has
	 * been called.
	 */
	@UiThread
	public void cancel() {
		if (task == null) return; // Already sent or cancelled
		task.cancel();
		// Observe the task until it finishes (which may already have
		// happened) and delete any created attachments
		LiveData<AttachmentResult> taskResult = task.getResult();
		taskResult.observeForever(new Observer<AttachmentResult>() {
			@Override
			public void onChanged(@Nullable AttachmentResult result) {
				requireNonNull(result);
				if (result.isFinished()) {
					deleteUnsentAttachments(result.getItemResults());
					taskResult.removeObserver(this);
				}
			}
		});
		task = null;
	}

	private void deleteUnsentAttachments(
			Collection<AttachmentItemResult> itemResults) {
		List<AttachmentHeader> headers = new ArrayList<>(itemResults.size());
		for (AttachmentItemResult itemResult : itemResults) {
			AttachmentItem item = itemResult.getItem();
			if (item != null) headers.add(item.getHeader());
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
