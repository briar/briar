package org.briarproject.briar.android.attachment;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchMessageException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.attachment.AttachmentItem.State;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.briar.api.messaging.PrivateMessageHeader;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.briar.android.attachment.AttachmentItem.State.AVAILABLE;
import static org.briarproject.briar.android.attachment.AttachmentItem.State.ERROR;
import static org.briarproject.briar.android.attachment.AttachmentItem.State.LOADING;
import static org.briarproject.briar.android.attachment.AttachmentItem.State.MISSING;

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

	// Info for AttachmentItems that are either still LOADING or MISSING
	private final Map<MessageId, UnavailableItem> unavailableItems =
			new ConcurrentHashMap<>();
	// We cache only items in their final state: AVAILABLE or ERROR
	private final Map<MessageId, AttachmentItem> itemCache =
			new ConcurrentHashMap<>();

	@Inject
	AttachmentRetrieverImpl(MessagingManager messagingManager,
			AttachmentDimensions dimensions, ImageHelper imageHelper,
			ImageSizeCalculator imageSizeCalculator) {
		this.messagingManager = messagingManager;
		this.imageHelper = imageHelper;
		this.imageSizeCalculator = imageSizeCalculator;
		defaultSize = dimensions.defaultSize;
		minWidth = dimensions.minWidth;
		maxWidth = dimensions.maxWidth;
		minHeight = dimensions.minHeight;
		maxHeight = dimensions.maxHeight;
	}

	@Override
	@DatabaseExecutor
	public Attachment getMessageAttachment(AttachmentHeader h)
			throws DbException {
		return messagingManager.getAttachment(h);
	}

	@Override
	public List<AttachmentItem> getAttachmentItems(
			PrivateMessageHeader messageHeader) {
		List<AttachmentHeader> headers = messageHeader.getAttachmentHeaders();
		List<AttachmentItem> items = new ArrayList<>(headers.size());
		boolean needsSize = headers.size() == 1;
		for (AttachmentHeader h : headers) {
			AttachmentItem item = itemCache.get(h.getMessageId());
			if (item == null || (needsSize && !item.hasSize())) {
				item = new AttachmentItem(h, defaultSize, defaultSize, LOADING);
				UnavailableItem unavailableItem = new UnavailableItem(
						messageHeader.getId(), h, needsSize);
				unavailableItems.put(h.getMessageId(), unavailableItem);
			}
			items.add(item);
		}
		return items;
	}

	@Override
	@DatabaseExecutor
	public void cacheAttachmentItem(MessageId conversationMessageId,
			AttachmentHeader h) throws DbException {
		try {
			Attachment a = messagingManager.getAttachment(h);
			// this adds it to the cache automatically
			createAttachmentItem(a, true);
		} catch (NoSuchMessageException e) {
			LOG.info("Attachment not received yet");
		}
	}

	@Override
	@Nullable
	@DatabaseExecutor
	public Pair<MessageId, AttachmentItem> loadAttachmentItem(
			MessageId attachmentId) throws DbException {
		UnavailableItem unavailableItem = unavailableItems.get(attachmentId);
		if (unavailableItem == null) return null;

		MessageId conversationMessageId =
				unavailableItem.getConversationMessageId();
		AttachmentHeader h = unavailableItem.getHeader();
		boolean needsSize = unavailableItem.needsSize();

		AttachmentItem item;
		try {
			Attachment a = messagingManager.getAttachment(h);
			item = createAttachmentItem(a, needsSize);
			unavailableItems.remove(attachmentId);
		} catch (NoSuchMessageException e) {
			LOG.info("Attachment not received yet");
			// unavailable item is still tracked, no need to add it again
			item = new AttachmentItem(h, defaultSize, defaultSize, MISSING);
		}
		return new Pair<>(conversationMessageId, item);
	}

	@Override
	public AttachmentItem createAttachmentItem(Attachment a,
			boolean needsSize) {
		AttachmentHeader h = a.getHeader();
		AttachmentItem item = itemCache.get(h.getMessageId());
		if (item != null && (needsSize && item.hasSize())) return item;

		if (needsSize) {
			InputStream is = new BufferedInputStream(a.getStream());
			Size size = imageSizeCalculator.getSize(is, h.getContentType());
			item = createAttachmentItem(h, size);
		} else {
			String extension =
					imageHelper.getExtensionFromMimeType(h.getContentType());
			State state = AVAILABLE;
			if (extension == null) {
				extension = "";
				state = ERROR;
			}
			item = new AttachmentItem(h, extension, state);
		}
		itemCache.put(h.getMessageId(), item);
		return item;
	}

	private AttachmentItem createAttachmentItem(AttachmentHeader h, Size size) {
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
		State state = hasError ? ERROR : AVAILABLE;
		return new AttachmentItem(h, size.width, size.height,
				extension, thumbnailSize.width, thumbnailSize.height, state);
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
