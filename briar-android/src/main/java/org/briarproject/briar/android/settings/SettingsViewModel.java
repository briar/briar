package org.briarproject.briar.android.settings;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.settings.Settings;
import org.briarproject.bramble.api.settings.SettingsManager;
import org.briarproject.bramble.api.settings.event.SettingsUpdatedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.LocationUtils;
import org.briarproject.bramble.plugin.tor.CircumventionProvider;
import org.briarproject.briar.android.attachment.UnsupportedMimeTypeException;
import org.briarproject.briar.android.attachment.media.ImageCompressor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;
import org.briarproject.briar.api.avatar.AvatarManager;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.Arrays.asList;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.now;

@NotNullByDefault
class SettingsViewModel extends DbViewModel implements EventListener {

	private final static Logger LOG =
			getLogger(SettingsViewModel.class.getName());

	public static final String SETTINGS_NAMESPACE = "android-ui";
	static final String BT_NAMESPACE =
			BluetoothConstants.ID.getString();
	static final String WIFI_NAMESPACE = LanTcpConstants.ID.getString();
	static final String TOR_NAMESPACE = TorConstants.ID.getString();

	private final SettingsManager settingsManager;
	private final IdentityManager identityManager;
	private final EventBus eventBus;
	private final AvatarManager avatarManager;
	private final AuthorManager authorManager;
	private final ImageCompressor imageCompressor;
	private final Executor ioExecutor;
	private final ConnectionsManager connectionsManager;

	final SettingsStore settingsStore;
	final TorSummaryProvider torSummaryProvider;

	private volatile Settings settings;

	private final MutableLiveData<OwnIdentityInfo> ownIdentityInfo =
			new MutableLiveData<>();
	private final MutableLiveEvent<Boolean> setAvatarFailed =
			new MutableLiveEvent<>();

	@Inject
	SettingsViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			SettingsManager settingsManager,
			IdentityManager identityManager,
			EventBus eventBus,
			AvatarManager avatarManager,
			AuthorManager authorManager,
			ImageCompressor imageCompressor,
			LocationUtils locationUtils,
			CircumventionProvider circumventionProvider,
			@IoExecutor Executor ioExecutor) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.settingsManager = settingsManager;
		this.identityManager = identityManager;
		this.eventBus = eventBus;
		this.imageCompressor = imageCompressor;
		this.avatarManager = avatarManager;
		this.authorManager = authorManager;
		this.ioExecutor = ioExecutor;
		this.settingsStore = new SettingsStore(settingsManager, dbExecutor,
				SETTINGS_NAMESPACE);
		this.torSummaryProvider = new TorSummaryProvider(getApplication(),
				locationUtils, circumventionProvider);
		this.connectionsManager =
				new ConnectionsManager(settingsManager, dbExecutor);

		eventBus.addListener(this);
		loadSettings();
		loadOwnIdentityInfo();
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	private void loadSettings() {
		runOnDbThread(() -> {
			try {
				long start = now();
				settings = settingsManager.getSettings(SETTINGS_NAMESPACE);
				connectionsManager.updateBtSetting(
						settingsManager.getSettings(BT_NAMESPACE));
				connectionsManager.updateWifiSettings(
						settingsManager.getSettings(WIFI_NAMESPACE));
				connectionsManager.updateTorSettings(
						settingsManager.getSettings(TOR_NAMESPACE));
				logDuration(LOG, "Loading settings", start);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	private void loadOwnIdentityInfo() {
		runOnDbThread(() -> {
			try {
				LocalAuthor localAuthor = identityManager.getLocalAuthor();
				AuthorInfo authorInfo = authorManager.getMyAuthorInfo();
				ownIdentityInfo.postValue(
						new OwnIdentityInfo(localAuthor, authorInfo));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof SettingsUpdatedEvent) {
			SettingsUpdatedEvent s = (SettingsUpdatedEvent) e;
			String namespace = s.getNamespace();
			if (namespace.equals(BT_NAMESPACE)) {
				LOG.info("Bluetooth settings updated");
				connectionsManager.updateBtSetting(s.getSettings());
			} else if (namespace.equals(WIFI_NAMESPACE)) {
				LOG.info("Wifi settings updated");
				connectionsManager.updateWifiSettings(s.getSettings());
			} else if (namespace.equals(TOR_NAMESPACE)) {
				LOG.info("Tor settings updated");
				connectionsManager.updateTorSettings(s.getSettings());
			}
		}
	}

	void setAvatar(Uri uri) {
		ioExecutor.execute(() -> {
			try {
				trySetAvatar(uri);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				setAvatarFailed.postEvent(true);
			}
		});
	}

	private void trySetAvatar(Uri uri) throws IOException {
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
		InputStream compressed = imageCompressor.compressImage(is, contentType);

		runOnDbThread(() -> {
			try {
				avatarManager.addAvatar(ImageCompressor.MIME_TYPE, compressed);
				loadOwnIdentityInfo();
			} catch (IOException | DbException e) {
				logException(LOG, WARNING, e);
				setAvatarFailed.postEvent(true);
			}
		});
	}

	LiveData<OwnIdentityInfo> getOwnIdentityInfo() {
		return ownIdentityInfo;
	}

	LiveEvent<Boolean> getSetAvatarFailed() {
		return setAvatarFailed;
	}

	ConnectionsManager getConnectionsManager() {
		return connectionsManager;
	}

}
