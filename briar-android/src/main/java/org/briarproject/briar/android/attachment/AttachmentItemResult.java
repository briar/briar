package org.briarproject.briar.android.attachment;

import android.net.Uri;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentItemResult {

	private final Uri uri;
	@Nullable
	private final AttachmentItem item;
	@Nullable
	private final Exception exception;

	AttachmentItemResult(Uri uri, AttachmentItem item) {
		this.uri = uri;
		this.item = item;
		this.exception = null;
	}

	AttachmentItemResult(Uri uri, Exception exception) {
		this.uri = uri;
		this.item = null;
		this.exception = exception;
	}

	public Uri getUri() {
		return uri;
	}

	@Nullable
	public AttachmentItem getItem() {
		return item;
	}

	public boolean hasError() {
		return item == null;
	}

	@Nullable
	public Exception getException() {
		return exception;
	}

}
