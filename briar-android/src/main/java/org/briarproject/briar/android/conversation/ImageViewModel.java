package org.briarproject.briar.android.conversation;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.media.MediaScannerConnection.scanFile;
import static android.os.Environment.DIRECTORY_PICTURES;
import static android.os.Environment.getExternalStoragePublicDirectory;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.copyAndClose;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class ImageViewModel extends AndroidViewModel {

	private static Logger LOG = getLogger(ImageViewModel.class.getName());

	private final MessagingManager messagingManager;
	@DatabaseExecutor
	private final Executor dbExecutor;
	@IoExecutor
	private final Executor ioExecutor;

	private MutableLiveData<Boolean> saveState = new MutableLiveData<>();

	@Inject
	public ImageViewModel(Application application,
			MessagingManager messagingManager,
			@DatabaseExecutor Executor dbExecutor,
			@IoExecutor Executor ioExecutor) {
		super(application);
		this.messagingManager = messagingManager;
		this.dbExecutor = dbExecutor;
		this.ioExecutor = ioExecutor;
	}

	/**
	 * A LiveData that is true if the image was saved,
	 * false if there was an error and null otherwise.
	 *
	 * Call {@link #onSaveStateSeen()} after consuming an update.
	 */
	LiveData<Boolean> getSaveState() {
		return saveState;
	}

	@UiThread
	void onSaveStateSeen() {
		saveState.setValue(null);
	}

	/**
	 * Saves the attachment to a writeable {@link Uri}.
	 */
	@UiThread
	void saveImage(AttachmentItem attachment, @Nullable Uri uri) {
		if (uri == null) {
			saveState.setValue(false);
		} else {
			saveImage(attachment, () -> getOutputStream(uri), null);
		}
	}

	/**
	 * Saves the attachment on external storage,
	 * assuming the permission was granted during install time.
	 */
	void saveImage(AttachmentItem attachment) {
		File file = getImageFile(attachment);
		saveImage(attachment, () -> getOutputStream(file), () -> {
			scanFile(getApplication(), new String[] {file.toString()}, null,
					null);
		});
	}

	private void saveImage(AttachmentItem attachment, OutputStreamProvider osp,
			@Nullable Runnable afterCopy) {
		MessageId messageId = attachment.getMessageId();
		dbExecutor.execute(() -> {
			try {
				Attachment a = messagingManager.getAttachment(messageId);
				copyImageFromDb(a, osp, afterCopy);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				saveState.postValue(true);
			}
		});
	}

	private void copyImageFromDb(Attachment a, OutputStreamProvider osp,
			@Nullable Runnable afterCopy) {
		ioExecutor.execute(() -> {
			try {
				InputStream is = a.getStream();
				OutputStream os = osp.getOutputStream();
				copyAndClose(is, os);
				if (afterCopy != null) afterCopy.run();
				saveState.postValue(false);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				saveState.postValue(true);
			}
		});
	}

	private String getFileName() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
				Locale.getDefault());
		return sdf.format(new Date());
	}

	private File getImageFile(AttachmentItem attachment) {
		File path = getExternalStoragePublicDirectory(DIRECTORY_PICTURES);
		//noinspection ResultOfMethodCallIgnored
		path.mkdirs();
		String fileName = getFileName();
		String ext = attachment.getMimeType().replaceFirst("image/", ".");
		File file = new File(path, fileName + ext);
		int i = 1;
		while (file.exists()) {
			file = new File(path, fileName + " (" + i + ")" + ext);
		}
		return file;
	}

	private OutputStream getOutputStream(File file) throws IOException {
		return new FileOutputStream(file);
	}

	private OutputStream getOutputStream(Uri uri) throws IOException {
		OutputStream os =
				getApplication().getContentResolver().openOutputStream(uri);
		if (os == null) throw new IOException();
		return os;
	}

	private interface OutputStreamProvider {
		OutputStream getOutputStream() throws IOException;
	}

}
