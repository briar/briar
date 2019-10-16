package org.briarproject.briar.android.attachment;

import androidx.lifecycle.LiveData;
import android.net.Uri;
import androidx.annotation.UiThread;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.messaging.AttachmentHeader;

import java.util.Collection;
import java.util.List;

@UiThread
@NotNullByDefault
public interface AttachmentManager {

	LiveData<AttachmentResult> storeAttachments(Collection<Uri> uri,
			boolean restart);

	List<AttachmentHeader> getAttachmentHeadersForSending();

	void cancel();

}
