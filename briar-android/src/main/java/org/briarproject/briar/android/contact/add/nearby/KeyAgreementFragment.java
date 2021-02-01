package org.briarproject.briar.android.contact.add.nearby;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadEncoder;
import org.briarproject.bramble.api.keyagreement.PayloadParser;
import org.briarproject.bramble.api.keyagreement.event.KeyAgreementListeningEvent;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState;
import org.briarproject.briar.android.fragment.BaseEventFragment;
import org.briarproject.briar.android.view.QrCodeView;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

import androidx.annotation.UiThread;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.ABORTED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.FAILED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.FINISHED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.STARTED;
import static org.briarproject.briar.android.contact.add.nearby.ContactExchangeViewModel.KeyAgreementState.WAITING;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class KeyAgreementFragment extends BaseEventFragment
		implements QrCodeDecoder.ResultCallback, QrCodeView.FullscreenListener {

	static final String TAG = KeyAgreementFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);
	@SuppressWarnings("CharsetObjectCanBeUsed") // Requires minSdkVersion >= 19
	private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	Provider<KeyAgreementTask> keyAgreementTaskProvider;
	@Inject
	PayloadEncoder payloadEncoder;
	@Inject
	PayloadParser payloadParser;
	@Inject
	@IoExecutor
	Executor ioExecutor;
	@Inject
	EventBus eventBus;

	private ContactExchangeViewModel viewModel;
	private CameraView cameraView;
	private LinearLayout cameraOverlay;
	private View statusView;
	private QrCodeView qrCodeView;
	private TextView status;

	private boolean gotRemotePayload;
	private volatile boolean gotLocalPayload;
	private KeyAgreementTask task;

	public static KeyAgreementFragment newInstance() {
		Bundle args = new Bundle();
		KeyAgreementFragment fragment = new KeyAgreementFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(ContactExchangeViewModel.class);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_keyagreement_qr, container,
				false);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		cameraView = view.findViewById(R.id.camera_view);
		cameraOverlay = view.findViewById(R.id.camera_overlay);
		statusView = view.findViewById(R.id.status_container);
		status = view.findViewById(R.id.connect_status);
		qrCodeView = view.findViewById(R.id.qr_code_view);
		qrCodeView.setFullscreenListener(this);

		LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
		viewModel.getKeyAgreementState()
				.observe(lifecycleOwner, this::onKeyAgreementStateChanged);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		requireActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);
		cameraView.setPreviewConsumer(new QrCodeDecoder(this));
	}

	@Override
	public void onStart() {
		super.onStart();
		try {
			cameraView.start();
		} catch (CameraException e) {
			logCameraExceptionAndFinish(e);
		}
		startListening();
	}

	@Override
	public void setFullscreen(boolean fullscreen) {
		LinearLayout.LayoutParams statusParams, qrCodeParams;
		if (fullscreen) {
			// Grow the QR code view to fill its parent
			statusParams = new LayoutParams(0, 0, 0f);
			qrCodeParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT, 1f);
		} else {
			// Shrink the QR code view to fill half its parent
			if (cameraOverlay.getOrientation() == HORIZONTAL) {
				statusParams = new LayoutParams(0, MATCH_PARENT, 1f);
				qrCodeParams = new LayoutParams(0, MATCH_PARENT, 1f);
			} else {
				statusParams = new LayoutParams(MATCH_PARENT, 0, 1f);
				qrCodeParams = new LayoutParams(MATCH_PARENT, 0, 1f);
			}
		}
		statusView.setLayoutParams(statusParams);
		qrCodeView.setLayoutParams(qrCodeParams);
		cameraOverlay.invalidate();
	}

	@Override
	public void onStop() {
		super.onStop();
		stopListening();
		try {
			cameraView.stop();
		} catch (CameraException e) {
			logCameraExceptionAndFinish(e);
		}
	}

	@UiThread
	private void logCameraExceptionAndFinish(CameraException e) {
		logException(LOG, WARNING, e);
		Toast.makeText(getActivity(), R.string.camera_error,
				LENGTH_LONG).show();
		finish();
	}

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

	@UiThread
	private void reset() {
		// If we've stopped the camera view, restart it
		if (gotRemotePayload) {
			try {
				cameraView.start();
			} catch (CameraException e) {
				logCameraExceptionAndFinish(e);
				return;
			}
		}
		statusView.setVisibility(INVISIBLE);
		cameraView.setVisibility(VISIBLE);
		gotRemotePayload = false;
		gotLocalPayload = false;
		startListening();
	}

	@UiThread
	private void qrCodeScanned(String content) {
		try {
			byte[] payloadBytes = content.getBytes(ISO_8859_1);
			if (LOG.isLoggable(INFO))
				LOG.info("Remote payload is " + payloadBytes.length + " bytes");
			Payload remotePayload = payloadParser.parse(payloadBytes);
			gotRemotePayload = true;
			cameraView.stop();
			cameraView.setVisibility(INVISIBLE);
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.connecting_to_device);
			task.connectAndRunProtocol(remotePayload);
		} catch (UnsupportedVersionException e) {
			reset();
			String msg;
			if (e.isTooOld()) {
				msg = getString(R.string.qr_code_too_old,
						getString(R.string.app_name));
			} else {
				msg = getString(R.string.qr_code_too_new,
						getString(R.string.app_name));
			}
			showNextFragment(ContactExchangeErrorFragment.newInstance(msg));
		} catch (CameraException e) {
			logCameraExceptionAndFinish(e);
		} catch (IOException | IllegalArgumentException e) {
			LOG.log(WARNING, "QR Code Invalid", e);
			reset();
			Toast.makeText(getActivity(), R.string.qr_code_invalid,
					LENGTH_LONG).show();
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementListeningEvent) {
			KeyAgreementListeningEvent event = (KeyAgreementListeningEvent) e;
			gotLocalPayload = true;
			setQrCode(event.getLocalPayload());
		}
	}

	@UiThread
	private void onKeyAgreementStateChanged(KeyAgreementState state) {
		if (state == WAITING) {
			status.setText(R.string.waiting_for_contact_to_scan);
		} else if (state == STARTED) {
			qrCodeView.setVisibility(INVISIBLE);
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.authenticating_with_device);
		} else if (state == FINISHED) {
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.exchanging_contact_details);
		} else if (state == ABORTED || state == FAILED) {
			reset();
		}
	}

	private void setQrCode(Payload localPayload) {
		Context context = getContext();
		if (context == null) return;
		DisplayMetrics dm = context.getResources().getDisplayMetrics();
		ioExecutor.execute(() -> {
			byte[] payloadBytes = payloadEncoder.encode(localPayload);
			if (LOG.isLoggable(INFO)) {
				LOG.info("Local payload is " + payloadBytes.length
						+ " bytes");
			}
			// Use ISO 8859-1 to encode bytes directly as a string
			String content = new String(payloadBytes, ISO_8859_1);
			Bitmap qrCode = QrCodeUtils.createQrCode(dm, content);
			runOnUiThreadUnlessDestroyed(
					() -> qrCodeView.setQrCode(qrCode));
		});
	}

	@Override
	public void handleResult(Result result) {
		runOnUiThreadUnlessDestroyed(() -> {
			LOG.info("Got result from decoder");
			// Ignore results until the KeyAgreementTask is ready
			if (!gotLocalPayload) return;
			if (!gotRemotePayload) qrCodeScanned(result.getText());
		});
	}

	@Override
	protected void finish() {
		requireActivity().getSupportFragmentManager().popBackStack();
	}

}
