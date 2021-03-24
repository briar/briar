package org.briarproject.briar.android.contact.add.nearby;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactExchangeManager;
import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.ContactExistsException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.keyagreement.KeyAgreementResult;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.PayloadParser;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementAbortedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFailedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementFinishedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementListeningEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementStartedEvent;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementWaitingEvent;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.Plugin;
import org.briarproject.bramble.api.plugin.Plugin.State;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.TransportId;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;
import org.briarproject.bramble.api.plugin.event.TransportStateEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeFinished;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeResult.Error;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeResult.Success;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeStarted;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.KeyAgreementListening;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.KeyAgreementStarted;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.KeyAgreementWaiting;
import org.briarproject.briar.android.viewmodel.LiveEvent;
import org.briarproject.briar.android.viewmodel.MutableLiveEvent;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static android.bluetooth.BluetoothAdapter.ACTION_SCAN_MODE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_SCAN_MODE;
import static android.bluetooth.BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.plugin.Plugin.State.ACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.DISABLED;
import static org.briarproject.bramble.api.plugin.Plugin.State.INACTIVE;
import static org.briarproject.bramble.api.plugin.Plugin.State.STARTING_STOPPING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactPermissionManager.areEssentialPermissionsGranted;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.REFUSED;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.UNKNOWN;

@NotNullByDefault
class AddNearbyContactViewModel extends AndroidViewModel
		implements EventListener, QrCodeDecoder.ResultCallback {

	private static final Logger LOG =
			getLogger(AddNearbyContactViewModel.class.getName());

	enum BluetoothDecision {
		/**
		 * We haven't asked the user about Bluetooth discoverability.
		 */
		UNKNOWN,

		/**
		 * The device doesn't have a Bluetooth adapter.
		 */
		NO_ADAPTER,

		/**
		 * We're waiting for the user to accept or refuse discoverability.
		 */
		WAITING,

		/**
		 * The user has accepted discoverability.
		 */
		ACCEPTED,

		/**
		 * The user has refused discoverability.
		 */
		REFUSED
	}

	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	private final EventBus eventBus;
	private final AndroidExecutor androidExecutor;
	private final Executor ioExecutor;
	private final PluginManager pluginManager;
	private final PayloadEncoder payloadEncoder;
	private final PayloadParser payloadParser;
	private final Provider<KeyAgreementTask> keyAgreementTaskProvider;
	private final ContactExchangeManager contactExchangeManager;
	private final ConnectionManager connectionManager;

	private final MutableLiveEvent<Boolean> checkPermissions =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> requestBluetoothDiscoverable =
			new MutableLiveEvent<>();
	private final MutableLiveEvent<Boolean> showQrCodeFragment =
			new MutableLiveEvent<>();
	private final MutableLiveData<AddContactState> state =
			new MutableLiveData<>();

	final QrCodeDecoder qrCodeDecoder;
	final BroadcastReceiver bluetoothReceiver = new BluetoothStateReceiver();

	@Nullable
	private final BluetoothAdapter bt;
	@Nullable
	private final Plugin wifiPlugin, bluetoothPlugin;

	// UiThread
	private BluetoothDecision bluetoothDecision = BluetoothDecision.UNKNOWN;

	private boolean wasContinueClicked = false;
	private boolean isActivityResumed = false;

	/**
	 * Records whether we've enabled the wifi plugin so we don't enable it more
	 * than once.
	 */
	private boolean hasEnabledWifi = false;

	/**
	 * Records whether we've enabled the Bluetooth plugin so we don't enable it
	 * more than once.
	 */
	private boolean hasEnabledBluetooth = false;

	@Nullable
	private KeyAgreementTask task;
	private volatile boolean gotLocalPayload = false, gotRemotePayload = false;

	@Inject
	AddNearbyContactViewModel(Application app,
			EventBus eventBus,
			AndroidExecutor androidExecutor,
			@IoExecutor Executor ioExecutor,
			PluginManager pluginManager,
			PayloadEncoder payloadEncoder,
			PayloadParser payloadParser,
			Provider<KeyAgreementTask> keyAgreementTaskProvider,
			ContactExchangeManager contactExchangeManager,
			ConnectionManager connectionManager) {
		super(app);
		this.eventBus = eventBus;
		this.androidExecutor = androidExecutor;
		this.ioExecutor = ioExecutor;
		this.pluginManager = pluginManager;
		this.payloadEncoder = payloadEncoder;
		this.payloadParser = payloadParser;
		this.keyAgreementTaskProvider = keyAgreementTaskProvider;
		this.contactExchangeManager = contactExchangeManager;
		this.connectionManager = connectionManager;
		bt = BluetoothAdapter.getDefaultAdapter();
		wifiPlugin = pluginManager.getPlugin(LanTcpConstants.ID);
		bluetoothPlugin = pluginManager.getPlugin(BluetoothConstants.ID);
		qrCodeDecoder = new QrCodeDecoder(androidExecutor, ioExecutor, this);
		eventBus.addListener(this);
		IntentFilter filter = new IntentFilter(ACTION_SCAN_MODE_CHANGED);
		getApplication().registerReceiver(bluetoothReceiver, filter);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		getApplication().unregisterReceiver(bluetoothReceiver);
		eventBus.removeListener(this);
		stopListening();
	}

	@UiThread
	void onContinueClicked() {
		if (bluetoothDecision == REFUSED) {
			bluetoothDecision = UNKNOWN; // Ask again
		}
		wasContinueClicked = true;
		checkPermissions.setEvent(true);
		showQrCodeFragmentIfAllowed();
	}

	@UiThread
	boolean isBluetoothSupported() {
		return bt != null && bluetoothPlugin != null;
	}

	@UiThread
	boolean isWifiReady() {
		if (wifiPlugin == null) return true; // Continue without wifi
		State state = wifiPlugin.getState();
		// Wait for plugin to become enabled
		return state == ACTIVE || state == INACTIVE;
	}

	@UiThread
	boolean isBluetoothReady() {
		if (bt == null || bluetoothPlugin == null) {
			// Continue without Bluetooth
			return true;
		}
		if (bluetoothDecision == BluetoothDecision.UNKNOWN ||
				bluetoothDecision == BluetoothDecision.WAITING ||
				bluetoothDecision == BluetoothDecision.REFUSED) {
			// Wait for user to accept
			return false;
		}
		if (bt.getScanMode() != SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			// Wait for adapter to become discoverable
			return false;
		}
		// Wait for plugin to become active
		return bluetoothPlugin.getState() == ACTIVE;
	}

	@UiThread
	void enableWifiIfWeShould() {
		if (hasEnabledWifi) return;
		if (wifiPlugin == null) return;
		State state = wifiPlugin.getState();
		if (state == STARTING_STOPPING || state == DISABLED) {
			LOG.info("Enabling wifi plugin");
			hasEnabledWifi = true;
			pluginManager.setPluginEnabled(LanTcpConstants.ID, true);
		}
	}

	@UiThread
	void enableBluetoothIfWeShould() {
		if (bluetoothDecision != BluetoothDecision.ACCEPTED) return;
		if (hasEnabledBluetooth) return;
		if (bluetoothPlugin == null || !isBluetoothSupported()) return;
		State state = bluetoothPlugin.getState();
		if (state == STARTING_STOPPING || state == DISABLED) {
			LOG.info("Enabling Bluetooth plugin");
			hasEnabledBluetooth = true;
			pluginManager.setPluginEnabled(BluetoothConstants.ID, true);
		}
	}

	@UiThread
	void startAddingContact() {
		// If we return to the intro fragment, the continue button needs to be
		// clicked again before showing the QR code fragment
		wasContinueClicked = false;
		// If we return to the intro fragment, ask for Bluetooth
		// discoverability again before showing the QR code fragment
		bluetoothDecision = UNKNOWN;
		// If we return to the intro fragment, we may need to enable wifi and
		// Bluetooth again
		hasEnabledWifi = false;
		hasEnabledBluetooth = false;
		// start to listen with a KeyAgreementTask
		startListening();
		showQrCodeFragment.setEvent(true);
	}

	/**
	 * Call this once Bluetooth and Wi-Fi are ready to be used.
	 * It is possible to call this more than once over the ViewModel's lifetime.
	 */
	@UiThread
	private void startListening() {
		KeyAgreementTask oldTask = task;
		KeyAgreementTask newTask = keyAgreementTaskProvider.get();
		task = newTask;
		ioExecutor.execute(() -> {
			if (oldTask != null) oldTask.stopListening();
			newTask.listen();
		});
	}

	@UiThread
	private void stopListening() {
		KeyAgreementTask oldTask = task;
		ioExecutor.execute(() -> {
			if (oldTask != null) oldTask.stopListening();
		});
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportStateEvent) {
			TransportStateEvent t = (TransportStateEvent) e;
			if (t.getTransportId().equals(BluetoothConstants.ID)) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Bluetooth state changed to " + t.getState());
				}
				showQrCodeFragmentIfAllowed();
			} else if (t.getTransportId().equals(LanTcpConstants.ID)) {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Wifi state changed to " + t.getState());
				}
				showQrCodeFragmentIfAllowed();
			}
		} else if (e instanceof KeyAgreementListeningEvent) {
			LOG.info("KeyAgreementListeningEvent received");
			KeyAgreementListeningEvent event = (KeyAgreementListeningEvent) e;
			onLocalPayloadReceived(event.getLocalPayload());
		} else if (e instanceof KeyAgreementWaitingEvent) {
			LOG.info("KeyAgreementWaitingEvent received");
			state.setValue(new KeyAgreementWaiting());
		} else if (e instanceof KeyAgreementStartedEvent) {
			LOG.info("KeyAgreementStartedEvent received");
			state.setValue(new KeyAgreementStarted());
		} else if (e instanceof KeyAgreementFinishedEvent) {
			LOG.info("KeyAgreementFinishedEvent received");
			KeyAgreementResult result =
					((KeyAgreementFinishedEvent) e).getResult();
			startContactExchange(result);
			state.setValue(new ContactExchangeStarted());
		} else if (e instanceof KeyAgreementAbortedEvent) {
			LOG.info("KeyAgreementAbortedEvent received");
			resetPayloadFlags();
			state.setValue(new AddContactState.Failed());
		} else if (e instanceof KeyAgreementFailedEvent) {
			LOG.info("KeyAgreementFailedEvent received");
			resetPayloadFlags();
			state.setValue(new AddContactState.Failed());
		}
	}

	@SuppressWarnings("StatementWithEmptyBody")
	@UiThread
	void showQrCodeFragmentIfAllowed() {
		boolean permissionsGranted = areEssentialPermissionsGranted(
				getApplication(), isBluetoothSupported());
		if (isActivityResumed && wasContinueClicked && permissionsGranted) {
			if (isWifiReady() && isBluetoothReady()) {
				LOG.info("Wifi and Bluetooth are ready");
				startAddingContact();
			} else {
				enableWifiIfWeShould();
				if (bluetoothDecision == UNKNOWN) {
					requestBluetoothDiscoverable.setEvent(true);
				} else if (bluetoothDecision == REFUSED) {
					// Ask again when the user clicks "continue"
				} else {
					enableBluetoothIfWeShould();
				}
			}
		}
	}

	/**
	 * This sets the QR code by setting the state to KeyAgreementListening.
	 */
	private void onLocalPayloadReceived(Payload localPayload) {
		if (gotLocalPayload) return;
		DisplayMetrics dm = getApplication().getResources().getDisplayMetrics();
		ioExecutor.execute(() -> {
			byte[] payloadBytes = payloadEncoder.encode(localPayload);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Local payload is " + payloadBytes.length
						+ " bytes");
			}
			// Use ISO 8859-1 to encode bytes directly as a string
			String content = new String(payloadBytes, ISO_8859_1);
			Bitmap qrCode = QrCodeUtils.createQrCode(dm, content);
			gotLocalPayload = true;
			state.postValue(new KeyAgreementListening(qrCode));
		});
	}

	@Override
	@IoExecutor
	public void onQrCodeDecoded(Result result) {
		LOG.info("Got result from decoder");
		// Ignore results until the KeyAgreementTask is ready
		if (!gotLocalPayload || gotRemotePayload) return;
		try {
			byte[] payloadBytes = result.getText().getBytes(ISO_8859_1);
			if (LOG.isLoggable(INFO))
				LOG.info("Remote payload is " + payloadBytes.length + " bytes");
			Payload remotePayload = payloadParser.parse(payloadBytes);
			gotRemotePayload = true;
			requireNonNull(task).connectAndRunProtocol(remotePayload);
			state.postValue(new AddContactState.QrCodeScanned());
		} catch (UnsupportedVersionException e) {
			resetPayloadFlags();
			state.postValue(new AddContactState.Failed(e.isTooOld()));
		} catch (IOException | IllegalArgumentException e) {
			LOG.log(WARNING, "QR Code Invalid", e);
			androidExecutor.runOnUiThread(() -> Toast.makeText(getApplication(),
					R.string.qr_code_invalid, LENGTH_LONG).show());
			resetPayloadFlags();
			state.postValue(new AddContactState.Failed());
		}
	}

	private void resetPayloadFlags() {
		gotRemotePayload = false;
		gotLocalPayload = false;
	}

	@UiThread
	private void startContactExchange(KeyAgreementResult result) {
		TransportId t = result.getTransportId();
		DuplexTransportConnection conn = result.getConnection();
		SecretKey masterKey = result.getMasterKey();
		boolean alice = result.wasAlice();
		ioExecutor.execute(() -> {
			try {
				Contact contact = contactExchangeManager.exchangeContacts(conn,
						masterKey, alice, true);
				// Reuse the connection as a transport connection
				connectionManager
						.manageOutgoingConnection(contact.getId(), t, conn);
				Success success = new Success(contact.getAuthor());
				state.postValue(new ContactExchangeFinished(success));
			} catch (ContactExistsException e) {
				tryToClose(conn);
				Error error = new Error(e.getRemoteAuthor());
				state.postValue(new ContactExchangeFinished(error));
			} catch (DbException | IOException e) {
				tryToClose(conn);
				logException(LOG, WARNING, e);
				Error error = new Error(null);
				state.postValue(new ContactExchangeFinished(error));
			}
		});
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {
		@UiThread
		@Override
		public void onReceive(Context context, Intent intent) {
			int scanMode = intent.getIntExtra(EXTRA_SCAN_MODE, -1);
			LOG.info("Bluetooth scan mode changed: " + scanMode);
			showQrCodeFragmentIfAllowed();
		}
	}

	private void tryToClose(DuplexTransportConnection conn) {
		try {
			conn.getReader().dispose(true, true);
			conn.getWriter().dispose(true);
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	/**
	 * Set to true in onPostResume() and false in onPause(). This prevents the
	 * QR code fragment from being shown if onRequestPermissionsResult() is
	 * called while the activity is paused, which could cause a crash due to
	 * https://issuetracker.google.com/issues/37067655.
	 * TODO check if this is still happening with new permission requesting
	 */
	@UiThread
	void setIsActivityResumed(boolean resumed) {
		isActivityResumed = resumed;
		// Workaround for
		// https://code.google.com/p/android/issues/detail?id=190966
		showQrCodeFragmentIfAllowed();
	}

	@UiThread
	void setBluetoothDecision(BluetoothDecision decision) {
		bluetoothDecision = decision;
		showQrCodeFragmentIfAllowed();
	}

	LiveEvent<Boolean> getCheckPermissions() {
		return checkPermissions;
	}

	LiveEvent<Boolean> getRequestBluetoothDiscoverable() {
		return requestBluetoothDiscoverable;
	}

	LiveEvent<Boolean> getShowQrCodeFragment() {
		return showQrCodeFragment;
	}

	LiveData<AddContactState> getState() {
		return state;
	}

}
