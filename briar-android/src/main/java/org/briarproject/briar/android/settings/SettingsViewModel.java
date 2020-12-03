package org.briarproject.briar.android.settings;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.LogUtils;
import org.briarproject.briar.android.attachment.ImageCompressor;
import org.briarproject.briar.android.attachment.UnsupportedMimeTypeException;
import org.briarproject.briar.api.avatar.AvatarManager;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.Arrays.asList;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;

@NotNullByDefault
class SettingsViewModel extends AndroidViewModel {

	private final static Logger LOG =
			getLogger(SettingsViewModel.class.getName());

	private final IdentityManager identityManager;
	private final AvatarManager avatarManager;
	private final AuthorManager authorManager;
	private final ImageCompressor imageCompressor;
	@DatabaseExecutor
	private final Executor dbExecutor;

	private final MutableLiveData<OwnIdentityInfo> ownIdentityInfo =
			new MutableLiveData<>();

	@Inject
	SettingsViewModel(Application application,
			IdentityManager identityManager,
			AvatarManager avatarManager,
			AuthorManager authorManager,
			ImageCompressor imageCompressor,
			@DatabaseExecutor Executor dbExecutor) {
		super(application);
		this.identityManager = identityManager;
		this.imageCompressor = imageCompressor;
		this.avatarManager = avatarManager;
		this.authorManager = authorManager;
		this.dbExecutor = dbExecutor;

		loadOwnIdentityInfo();
	}

	LiveData<OwnIdentityInfo> getOwnIdentityInfo() {
		return ownIdentityInfo;
	}

	private void loadOwnIdentityInfo() {
		dbExecutor.execute(() -> {
			try {
				LocalAuthor localAuthor = identityManager.getLocalAuthor();
				AuthorInfo authorInfo = authorManager.getMyAuthorInfo();
				ownIdentityInfo.postValue(
						new OwnIdentityInfo(localAuthor, authorInfo));
			} catch (DbException e) {
				LogUtils.logException(LOG, Level.WARNING, e);
			}
		});
	}

	void setAvatar(Uri uri) {
		dbExecutor.execute(() -> {
			try {
				trySetAvatar(uri);
			} catch (IOException | DbException e) {
				LogUtils.logException(LOG, Level.WARNING, e);
			}
		});
	}

	private void trySetAvatar(Uri uri) throws IOException, DbException {
		ContentResolver contentResolver =
				getApplication().getContentResolver();
		String contentType = contentResolver.getType(uri);
		if (contentType == null) throw new IOException("null content type");
		if (!asList(getSupportedImageContentTypes()).contains(contentType)) {
			throw new UnsupportedMimeTypeException(contentType, uri);
		}
		InputStream is = contentResolver.openInputStream(uri);
		if (is == null) throw new IOException(
				"ContentResolver returned null when opening InputStream");
		is = imageCompressor
				.compressImage(is, contentType);
		avatarManager.addAvatar(ImageCompressor.MIME_TYPE, is);
		loadOwnIdentityInfo();
	}

}
