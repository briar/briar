package org.briarproject.briar.android.attachment;

import android.net.Uri;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentItemResult {

	@Nullable
	private final Uri uri;
	@Nullable
	private final AttachmentItem item;
	@Nullable
	private final String errorMsg;

	public AttachmentItemResult(Uri uri, AttachmentItem item) {
		this.uri = uri;
		this.item = item;
		this.errorMsg = null;
	}

	public AttachmentItemResult(@Nullable String errorMsg) {
		this.uri = null;
		this.item = null;
		this.errorMsg = errorMsg;
	}

	@Nullable
	public Uri getUri() {
		return uri;
	}

	@Nullable
	public AttachmentItem getItem() {
		return item;
	}

	public boolean isError() {
		return errorMsg != null;
	}

	@Nullable
	public String getErrorMsg() {
		return errorMsg;
	}

}
