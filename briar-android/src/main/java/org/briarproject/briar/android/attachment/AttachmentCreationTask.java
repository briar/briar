package org.briarproject.briar.android.attachment;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.net.Uri;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.api.media.AttachmentHeader;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.logging.Logger;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static android.graphics.BitmapFactory.decodeStream;
import static java.util.Arrays.asList;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.briar.api.messaging.MessagingConstants.MAX_IMAGE_SIZE;

@NotNullByDefault
class AttachmentCreationTask {

	private static Logger LOG =
			getLogger(AttachmentCreationTask.class.getName());

	private static final int MAX_ATTACHMENT_DIMENSION = 1000;

	private final MessagingManager messagingManager;
	private final ContentResolver contentResolver;
	private final ImageSizeCalculator imageSizeCalculator;
	private final GroupId groupId;
	private final Collection<Uri> uris;
	private final boolean needsSize;
	@Nullable
	private volatile AttachmentCreator attachmentCreator;

	private volatile boolean canceled = false;

	AttachmentCreationTask(MessagingManager messagingManager,
			ContentResolver contentResolver,
			AttachmentCreator attachmentCreator,
			ImageSizeCalculator imageSizeCalculator,
			GroupId groupId, Collection<Uri> uris, boolean needsSize) {
		this.messagingManager = messagingManager;
		this.contentResolver = contentResolver;
		this.imageSizeCalculator = imageSizeCalculator;
		this.groupId = groupId;
		this.uris = uris;
		this.needsSize = needsSize;
		this.attachmentCreator = attachmentCreator;
	}

	void cancel() {
		canceled = true;
		attachmentCreator = null;
	}

	@IoExecutor
	void storeAttachments() {
		for (Uri uri : uris) processUri(uri);
		AttachmentCreator attachmentCreator = this.attachmentCreator;
		if (!canceled && attachmentCreator != null)
			attachmentCreator.onAttachmentCreationFinished();
		this.attachmentCreator = null;
	}

	@IoExecutor
	private void processUri(Uri uri) {
		if (canceled) return;
		try {
			AttachmentHeader h = storeAttachment(uri);
			AttachmentCreator attachmentCreator = this.attachmentCreator;
			if (attachmentCreator != null) {
				attachmentCreator.onAttachmentHeaderReceived(uri, h, needsSize);
			}
		} catch (DbException | IOException e) {
			logException(LOG, WARNING, e);
			AttachmentCreator attachmentCreator = this.attachmentCreator;
			if (attachmentCreator != null) {
				attachmentCreator.onAttachmentError(uri, e);
			}
			canceled = true;
		}
	}

	@IoExecutor
	private AttachmentHeader storeAttachment(Uri uri)
			throws IOException, DbException {
		long start = now();
		String contentType = contentResolver.getType(uri);
		if (contentType == null) throw new IOException("null content type");
		if (!asList(getSupportedImageContentTypes()).contains(contentType)) {
			String uriString = uri.toString();
			throw new UnsupportedMimeTypeException("", contentType, uriString);
		}
		InputStream is = contentResolver.openInputStream(uri);
		if (is == null) throw new IOException();
		is = compressImage(is, contentType);
		contentType = "image/jpeg";
		long timestamp = System.currentTimeMillis();
		AttachmentHeader h = messagingManager
				.addLocalAttachment(groupId, timestamp, contentType, is);
		tryToClose(is, LOG, WARNING);
		logDuration(LOG, "Storing attachment", start);
		return h;
	}

	@VisibleForTesting
	InputStream compressImage(InputStream is, String contentType)
			throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			Bitmap bitmap = createBitmap(is, contentType);
			for (int quality = 100; quality >= 0; quality -= 10) {
				if (!bitmap.compress(JPEG, quality, out))
					throw new IOException();
				if (out.size() <= MAX_IMAGE_SIZE) {
					if (LOG.isLoggable(INFO)) {
						LOG.info("Compressed image to "
								+ out.size() + " bytes, quality " + quality);
					}
					return new ByteArrayInputStream(out.toByteArray());
				}
				out.reset();
			}
			throw new IOException();
		} finally {
			tryToClose(is, LOG, WARNING);
		}
	}

	private Bitmap createBitmap(InputStream is, String contentType)
			throws IOException {
		is = new BufferedInputStream(is);
		Size size = imageSizeCalculator.getSize(is, contentType);
		if (size.error) throw new IOException();
		if (LOG.isLoggable(INFO))
			LOG.info("Original image size: " + size.width + "x" + size.height);
		int dimension = Math.max(size.width, size.height);
		int inSampleSize = 1;
		while (dimension > MAX_ATTACHMENT_DIMENSION) {
			inSampleSize *= 2;
			dimension /= 2;
		}
		if (LOG.isLoggable(INFO))
			LOG.info("Scaling attachment by factor of " + inSampleSize);
		Options options = new Options();
		options.inSampleSize = inSampleSize;
		if (contentType.equals("image/png"))
			options.inPreferredConfig = Bitmap.Config.RGB_565;
		Bitmap bitmap = decodeStream(is, null, options);
		if (bitmap == null) throw new IOException();
		return bitmap;
	}
}
