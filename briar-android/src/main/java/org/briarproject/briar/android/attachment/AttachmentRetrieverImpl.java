package org.briarproject.briar.android.attachment;

import android.support.annotation.Nullable;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;

@NotNullByDefault
class AttachmentRetrieverImpl implements AttachmentRetriever {

	private static final Logger LOG =
			getLogger(AttachmentRetrieverImpl.class.getName());

	private final MessagingManager messagingManager;
	private final ImageHelper imageHelper;
	private final ImageSizeCalculator imageSizeCalculator;
	private final int defaultSize;
	private final int minWidth, maxWidth;
	private final int minHeight, maxHeight;

	private final Map<MessageId, List<AttachmentItem>> attachmentCache =
			new ConcurrentHashMap<>();

	@Inject
	AttachmentRetrieverImpl(MessagingManager messagingManager,
			AttachmentDimensions dimensions, ImageHelper imageHelper) {
		this.messagingManager = messagingManager;
		this.imageHelper = imageHelper;
		imageSizeCalculator = new ImageSizeCalculator(imageHelper);
		defaultSize = dimensions.defaultSize;
		minWidth = dimensions.minWidth;
		maxWidth = dimensions.maxWidth;
		minHeight = dimensions.minHeight;
		maxHeight = dimensions.maxHeight;
	}

	@Override
	public void cachePut(MessageId messageId,
			List<AttachmentItem> attachments) {
		attachmentCache.put(messageId, attachments);
	}

	@Override
	@Nullable
	public List<AttachmentItem> cacheGet(MessageId messageId) {
		return attachmentCache.get(messageId);
	}

	@Override
	public Attachment getMessageAttachment(AttachmentHeader h)
			throws DbException {
		return messagingManager.getAttachment(h);
	}

	@Override
	public AttachmentItem getAttachmentItem(Attachment a, boolean needsSize) {
		AttachmentHeader h = a.getHeader();
		if (!needsSize) {
			String extension =
					imageHelper.getExtensionFromMimeType(h.getContentType());
			boolean hasError = false;
			if (extension == null) {
				extension = "";
				hasError = true;
			}
			return new AttachmentItem(h, 0, 0, extension, 0, 0, hasError);
		}

		InputStream is = new BufferedInputStream(a.getStream());
		Size size = imageSizeCalculator.getSize(is, h.getContentType());

		// calculate thumbnail size
		Size thumbnailSize = new Size(defaultSize, defaultSize, size.mimeType);
		if (!size.error) {
			thumbnailSize =
					getThumbnailSize(size.width, size.height, size.mimeType);
		}
		// get file extension
		String extension = imageHelper.getExtensionFromMimeType(size.mimeType);
		boolean hasError = extension == null || size.error;
		if (!h.getContentType().equals(size.mimeType)) {
			if (LOG.isLoggable(WARNING)) {
				LOG.warning("Header has different mime type (" +
						h.getContentType() + ") than image (" + size.mimeType +
						").");
			}
			hasError = true;
		}
		if (extension == null) extension = "";
		return new AttachmentItem(h, size.width, size.height, extension,
				thumbnailSize.width, thumbnailSize.height, hasError);
	}

	private Size getThumbnailSize(int width, int height, String mimeType) {
		float widthPercentage = maxWidth / (float) width;
		float heightPercentage = maxHeight / (float) height;
		float scaleFactor = Math.min(widthPercentage, heightPercentage);
		if (scaleFactor > 1) scaleFactor = 1f;
		int thumbnailWidth = (int) (width * scaleFactor);
		int thumbnailHeight = (int) (height * scaleFactor);
		if (thumbnailWidth < minWidth || thumbnailHeight < minHeight) {
			widthPercentage = minWidth / (float) width;
			heightPercentage = minHeight / (float) height;
			scaleFactor = Math.max(widthPercentage, heightPercentage);
			thumbnailWidth = (int) (width * scaleFactor);
			thumbnailHeight = (int) (height * scaleFactor);
			if (thumbnailWidth > maxWidth) thumbnailWidth = maxWidth;
			if (thumbnailHeight > maxHeight) thumbnailHeight = maxHeight;
		}
		return new Size(thumbnailWidth, thumbnailHeight, mimeType);
	}
}
