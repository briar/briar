package org.briarproject.briar.android.attachment;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentResult {

	private final Collection<AttachmentItemResult> itemResults;
	private final boolean finished;
	private final boolean success;

	AttachmentResult(Collection<AttachmentItemResult> itemResults,
			boolean finished, boolean success) {
		this.itemResults = itemResults;
		this.finished = finished;
		this.success = success;
	}

	public Collection<AttachmentItemResult> getItemResults() {
		return itemResults;
	}

	public boolean isFinished() {
		return finished;
	}

	public boolean isSuccess() {
		return success;
	}
}
