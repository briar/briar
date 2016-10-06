package org.briarproject.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.api.AndroidExecutor;
import org.briarproject.android.fragment.BaseEventFragment;
import org.briarproject.android.util.QrCodeDecoder;
import org.briarproject.android.util.QrCodeUtils;
import org.briarproject.android.view.CameraView;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.KeyAgreementAbortedEvent;
import org.briarproject.api.event.KeyAgreementFailedEvent;
import org.briarproject.api.event.KeyAgreementFinishedEvent;
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
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;

public class ShowQrCodeFragment extends BaseEventFragment
		implements QrCodeDecoder.ResultCallback {

	private static final String TAG = ShowQrCodeFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

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
	private ViewGroup cameraOverlay;
	private View statusView;
	private TextView status;
	private ImageView qrCode;
	private ProgressBar mainProgressBar;
	private TextView mainProgressTitle;
	private ViewGroup mainProgressContainer;

	private BluetoothStateReceiver receiver;
	private QrCodeDecoder decoder;
	private boolean gotRemotePayload, waitingForBluetooth;
	private KeyAgreementTask task;

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
		cameraOverlay = (ViewGroup) view.findViewById(R.id.camera_overlay);
		statusView = view.findViewById(R.id.status_container);
		status = (TextView) view.findViewById(R.id.connect_status);
		qrCode = (ImageView) view.findViewById(R.id.qr_code);
		mainProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
		mainProgressTitle =
				(TextView) view.findViewById(R.id.title_progress_bar);
		mainProgressContainer =
				(ViewGroup) view.findViewById(R.id.container_progress);
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

	@UiThread
	private void startListening() {
		final KeyAgreementTask oldTask = task;
		final KeyAgreementTask newTask = keyAgreementTaskFactory.getTask();
		task = newTask;
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (oldTask != null) oldTask.stopListening();
				newTask.listen();
			}
		});
	}

	@UiThread
	private void stopListening() {
		final KeyAgreementTask oldTask = task;
		ioExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (oldTask != null) oldTask.stopListening();
			}
		});
	}

	@SuppressWarnings("deprecation")
	@UiThread
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

	@UiThread
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

	@UiThread
	private void reset() {
		statusView.setVisibility(INVISIBLE);
		cameraView.setVisibility(VISIBLE);
		gotRemotePayload = false;
		cameraView.startConsumer();
		startListening();
	}

	@UiThread
	private void qrCodeScanned(String content) {
		try {
			Payload remotePayload = payloadParser.parse(
					Base64.decode(content, 0));
			cameraView.setVisibility(INVISIBLE);
			statusView.setVisibility(VISIBLE);
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
		} else if (e instanceof KeyAgreementFinishedEvent) {
			listener.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					mainProgressContainer.setVisibility(VISIBLE);
					mainProgressTitle.setText(R.string.exchanging_contact_details);
				}
			});
		}
	}

	@UiThread
	private void generateBitmapQR(final Payload payload) {
		// Get narrowest screen dimension
		Context context = getContext();
		if (context == null) return;
		final DisplayMetrics dm = context.getResources().getDisplayMetrics();
		new AsyncTask<Void, Void, Bitmap>() {

			@Override
			protected Bitmap doInBackground(Void... params) {
				String input =
						Base64.encodeToString(payloadEncoder.encode(payload),
								0);
				Bitmap bitmap =
						QrCodeUtils.createQrCode(dm, input);
				return bitmap;
			}

			@Override
			protected void onPostExecute(Bitmap bitmap) {
				if (bitmap != null && !isDetached()) {
					qrCode.setImageBitmap(bitmap);
					// Simple fade-in animation
					AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
					anim.setDuration(200);
					qrCode.startAnimation(anim);
				}
			}
		}.execute();
	}

	private void setQrCode(final Payload localPayload) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				generateBitmapQR(localPayload);
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
				mainProgressContainer.setVisibility(VISIBLE);
				mainProgressTitle.setText(R.string.authenticating_with_device);
			}
		});
	}

	private void keyAgreementAborted(final boolean remoteAborted) {
		listener.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				reset();
				mainProgressContainer.setVisibility(INVISIBLE);
				mainProgressTitle.setText("");
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

	@Override
	protected void finish() {
		getActivity().getSupportFragmentManager().popBackStack();
	}

	private class BluetoothStateReceiver extends BroadcastReceiver {
		@UiThread
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
