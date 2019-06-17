package org.briarproject.briar.android.attachment;

import android.app.Application;
import android.arch.lifecycle.LiveData;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

import static java.util.Collections.emptyList;

@NotNullByDefault
public class AttachmentCreator {

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
		task = new AttachmentCreationTask(ioExecutor, messagingManager,
				app.getContentResolver(), retriever, groupId, uris, needsSize);
		task.storeAttachments();
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
	 * {@link #storeAttachments(GroupId, Collection)}, unless
	 * {@link #onAttachmentsSent()} or {@link #cancel()} has been called.
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
		task = null; // Prevent cancel() from cancelling the task
	}

	/**
	 * Cancels the task started by
	 * {@link #storeAttachments(GroupId, Collection)}, if any, unless
	 * {@link #onAttachmentsSent()} has been called.
	 */
	@UiThread
	public void cancel() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}
}
