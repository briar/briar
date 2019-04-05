package org.briarproject.briar.android.attachment;

import android.arch.lifecycle.LiveData;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AttachmentResult {

	private final Collection<LiveData<AttachmentItemResult>> itemResults;
	private final LiveData<Boolean> finished;

	public AttachmentResult(
			Collection<LiveData<AttachmentItemResult>> itemResults,
			LiveData<Boolean> finished) {
		this.itemResults = itemResults;
		this.finished = finished;
	}

	public Collection<LiveData<AttachmentItemResult>> getItemResults() {
		return itemResults;
	}

	public LiveData<Boolean> getFinished() {
		return finished;
	}

}
