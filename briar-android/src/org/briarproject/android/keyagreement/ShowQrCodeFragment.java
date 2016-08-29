package org.briarproject.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.fragment.BaseEventFragment;
import org.briarproject.android.util.CameraView;
import org.briarproject.android.util.QrCodeDecoder;
import org.briarproject.android.util.QrCodeUtils;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.KeyAgreementAbortedEvent;
import org.briarproject.api.event.KeyAgreementFailedEvent;
import org.briarproject.api.event.KeyAgreementListeningEvent;
import org.briarproject.api.event.KeyAgreementStartedEvent;
import org.briarproject.api.event.KeyAgreementWaitingEvent;
import org.briarproject.api.keyagreement.KeyAgreementTask;
import org.briarproject.api.keyagreement.KeyAgreementTaskFactory;
import org.briarproject.api.keyagreement.Payload;
import org.briarproject.api.keyagreement.PayloadEncoder;
import org.briarproject.api.keyagreement.PayloadParser;
import org.briarproject.api.lifecycle.IoExecutor;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

public class ShowQrCodeFragment extends BaseEventFragment
		implements QrCodeDecoder.ResultCallback {

	public static final String TAG = "ShowQrCodeFragment";

	private static final Logger LOG =
			Logger.getLogger(ShowQrCodeFragment.class.getName());

	@Inject
	protected KeyAgreementTaskFactory keyAgreementTaskFactory;
	@Inject
	protected PayloadEncoder payloadEncoder;
	@Inject
	protected PayloadParser payloadParser;
	@Inject
	protected AndroidExecutor androidExecutor;
	@Inject
	@IoExecutor
	protected Executor ioExecutor;

	private CameraView cameraView;
	private View statusView;
	private TextView status;
	private ImageView qrCode;

	private BluetoothStateReceiver receiver;
	private QrCodeDecoder decoder;
	private boolean gotRemotePayload;

	private volatile KeyAgreementTask task;
	private volatile boolean waitingForBluetooth;

	public static ShowQrCodeFragment newInstance() {
		
		Bundle args = new Bundle();
		
		ShowQrCodeFragment fragment = new ShowQrCodeFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_keyagreement_qr, container,
				false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		cameraView = (CameraView) view.findViewById(R.id.camera_view);
		statusView = view.findViewById(R.id.status_container);
		status = (TextView) view.findViewById(R.id.connect_status);
		qrCode = (ImageView) view.findViewById(R.id.qr_code);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);

		decoder = new QrCodeDecoder(this);
	}

	@Override
	public void onStart() {
		super.onStart();

		// Listen for changes to the Bluetooth state
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_STATE_CHANGED);
		receiver = new BluetoothStateReceiver();
		getActivity().registerReceiver(receiver, filter);

		// Enable BT adapter if it is not already on.
		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter != null && !adapter.isEnabled()) {
			waitingForBluetooth = true;
			androidExecutor.runOnBackgroundThread(new Runnable() {
				@Override
				public void run() {
					adapter.enable();
				}
			});
		} else {
			startListening();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		openCamera();
	}

	@Override
	public void onPause() {
		super.onPause();
		releaseCamera();
	}

	@Override
	public void onStop() {
		super.onStop();
		stopListening();
		if (receiver != null) getActivity().unregisterReceiver(receiver);
	}

	private void startListening() {
		task = keyAgreementTaskFactory.getTask();
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				task.listen();
			}
		});
	}

	private void stopListening() {
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				task.stopListening();
			}
		});
	}

	@SuppressWarnings("deprecation")
	private void openCamera() {
		LOG.info("Opening camera");
		Camera camera;
		try {
			camera = Camera.open();
		} catch (RuntimeException e) {
			LOG.log(WARNING, e.toString(), e);
			camera = null;
		}
		if (camera == null) {
			LOG.log(WARNING, "Error opening camera");
			Toast.makeText(getActivity(), R.string.could_not_open_camera,
					LENGTH_LONG).show();
			finish();
			return;
		}
		cameraView.start(camera, decoder, 0);
	}

	private void releaseCamera() {
		LOG.info("Releasing camera");
		try {
			cameraView.stop();
		} catch (RuntimeException e) {
			LOG.log(WARNING, "Error releasing camera", e);
			// TODO better solution
			finish();
		}
	}

	private void reset() {
		statusView.setVisibility(View.INVISIBLE);
		cameraView.setVisibility(View.VISIBLE);
		gotRemotePayload = false;
		cameraView.startConsumer();
		startListening();
	}

	private void qrCodeScanned(String content) {
		try {
			// TODO use Base32
			Payload remotePayload = payloadParser.parse(
					Base64.decode(content, 0));
			cameraView.setVisibility(View.INVISIBLE);
			statusView.setVisibility(View.VISIBLE);
			status.setText(R.string.connecting_to_device);
			task.connectAndRunProtocol(remotePayload);
		} catch (IOException e) {
			// TODO show failure
			Toast.makeText(getActivity(), R.string.qr_code_invalid,
					LENGTH_LONG).show();
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof KeyAgreementListeningEvent) {
			KeyAgreementListeningEvent event = (KeyAgreementListeningEvent) e;
			setQrCode(event.getLocalPayload());
		} else if (e instanceof KeyAgreementFailedEvent) {
			keyAgreementFailed();
		} else if (e instanceof KeyAgreementWaitingEvent) {
			keyAgreementWaiting();
		} else if (e instanceof KeyAgreementStartedEvent) {
			keyAgreementStarted();
		} else if (e instanceof KeyAgreementAbortedEvent) {
			KeyAgreementAbortedEvent event = (KeyAgreementAbortedEvent) e;
			keyAgreementAborted(event.didRemoteAbort());
		}
	}

	private void setQrCode(final Payload localPayload) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				// TODO use Base32
				String input = Base64.encodeToString(
						payloadEncoder.encode(localPayload), 0);
				qrCode.setImageBitmap(
						QrCodeUtils.createQrCode((Context) listener, input));
				// Simple fade-in animation
				AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
				anim.setDuration(200);
				qrCode.startAnimation(anim);
			}
		});
	}

	private void keyAgreementFailed() {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				reset();
				// TODO show failure somewhere persistent?
				Toast.makeText(getActivity(), R.string.connection_failed,
						LENGTH_LONG).show();
			}
		});
	}

	private void keyAgreementWaiting() {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				status.setText(R.string.waiting_for_contact);
			}
		});
	}

	private void keyAgreementStarted() {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				listener.showLoadingScreen(false,
						R.string.authenticating_with_device);
			}
		});
	}

	private void keyAgreementAborted(final boolean remoteAborted) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				reset();
				listener.hideLoadingScreen();
				// TODO show abort somewhere persistent?
				Toast.makeText(getActivity(),
						remoteAborted ? R.string.connection_aborted_remote :
								R.string.connection_aborted_local, LENGTH_LONG)
						.show();
			}
		});
	}

	@Override
	public void handleResult(final Result result) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Got result from decoder");
				if (!gotRemotePayload) {
					gotRemotePayload = true;
					cameraView.stopConsumer();
					qrCodeScanned(result.getText());
				}
			}
		});
	}

	private void finish() {
		getActivity().getSupportFragmentManager().popBackStack();
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context ctx, Intent intent) {
			int state = intent.getIntExtra(EXTRA_STATE, 0);
			if (state == STATE_ON && waitingForBluetooth) {
				LOG.info("Bluetooth enabled");
				waitingForBluetooth = false;
				startListening();
			}
		}
	}
}
