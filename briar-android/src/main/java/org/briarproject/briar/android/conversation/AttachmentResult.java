package org.briarproject.briar.android.conversation;

import android.net.Uri;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentResult {

	@Nullable
	private final Uri uri;
	@Nullable
	private final AttachmentItem item;
	@Nullable
	private final String errorMsg;

	public AttachmentResult(Uri uri, AttachmentItem item) {
		this.uri = uri;
		this.item = item;
		this.errorMsg = null;
	}

	public AttachmentResult(@Nullable String errorMsg) {
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
