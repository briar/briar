package org.briarproject.briar.android.attachment;

import android.net.Uri;
import android.support.annotation.UiThread;

import org.briarproject.briar.api.messaging.AttachmentHeader;

import java.util.Collection;
import java.util.List;

@UiThread
public interface AttachmentManager {

	AttachmentResult storeAttachments(Collection<Uri> uri, boolean restart);

	List<AttachmentHeader> getAttachmentHeadersForSending();

	void cancel();

}
