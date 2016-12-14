package org.briarproject.briar.android.keyagreement;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.UiThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTask;
import org.briarproject.bramble.api.keyagreement.KeyAgreementTaskFactory;
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
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseEventFragment;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED;
import static android.bluetooth.BluetoothAdapter.EXTRA_STATE;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.INFO;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ShowQrCodeFragment extends BaseEventFragment
		implements QrCodeDecoder.ResultCallback {

	private static final String TAG = ShowQrCodeFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	KeyAgreementTaskFactory keyAgreementTaskFactory;
	@Inject
	PayloadEncoder payloadEncoder;
	@Inject
	PayloadParser payloadParser;
	@Inject
	AndroidExecutor androidExecutor;
	@Inject
	@IoExecutor
	Executor ioExecutor;

	private CameraView cameraView;
	private View statusView;
	private TextView status;
	private ImageView qrCode;
	private TextView mainProgressTitle;
	private ViewGroup mainProgressContainer;

	private BluetoothStateReceiver receiver;
	private boolean gotRemotePayload, waitingForBluetooth;
	private volatile boolean gotLocalPayload;
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
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_keyagreement_qr, container,
				false);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		cameraView = (CameraView) view.findViewById(R.id.camera_view);
		statusView = view.findViewById(R.id.status_container);
		status = (TextView) view.findViewById(R.id.connect_status);
		qrCode = (ImageView) view.findViewById(R.id.qr_code);
		mainProgressTitle =
				(TextView) view.findViewById(R.id.title_progress_bar);
		mainProgressContainer =
				(ViewGroup) view.findViewById(R.id.container_progress);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		getActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);
		cameraView.setPreviewConsumer(new QrCodeDecoder(this));
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
		cameraView.start();
	}

	@Override
	public void onStop() {
		super.onStop();
		stopListening();
		if (receiver != null) getActivity().unregisterReceiver(receiver);
		cameraView.stop();
	}

	@UiThread
	private void startListening() {
		final KeyAgreementTask oldTask = task;
		final KeyAgreementTask newTask = keyAgreementTaskFactory.createTask();
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

	@UiThread
	private void reset() {
		statusView.setVisibility(INVISIBLE);
		cameraView.setVisibility(VISIBLE);
		gotRemotePayload = false;
		gotLocalPayload = false;
		startListening();
	}

	@UiThread
	private void qrCodeScanned(String content) {
		try {
			byte[] encoded = Base64.decode(content, 0);
			if (LOG.isLoggable(INFO))
				LOG.info("Remote payload is " + encoded.length + " bytes");
			Payload remotePayload = payloadParser.parse(encoded);
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
			gotLocalPayload = true;
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
			runOnUiThreadUnlessDestroyed(new Runnable() {
				@Override
				public void run() {
					mainProgressContainer.setVisibility(VISIBLE);
					mainProgressTitle.setText(
							R.string.exchanging_contact_details);
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
				byte[] encoded = payloadEncoder.encode(payload);
				if (LOG.isLoggable(INFO))
					LOG.info("Local payload is " + encoded.length + " bytes");
				String input = Base64.encodeToString(encoded, 0);
				return QrCodeUtils.createQrCode(dm, input);
			}

			@Override
			protected void onPostExecute(@Nullable Bitmap bitmap) {
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
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				generateBitmapQR(localPayload);
			}
		});
	}

	private void keyAgreementFailed() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
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
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				status.setText(R.string.waiting_for_contact_to_scan);
			}
		});
	}

	private void keyAgreementStarted() {
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				mainProgressContainer.setVisibility(VISIBLE);
				mainProgressTitle.setText(R.string.authenticating_with_device);
			}
		});
	}

	private void keyAgreementAborted(final boolean remoteAborted) {
		runOnUiThreadUnlessDestroyed(new Runnable() {
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
		runOnUiThreadUnlessDestroyed(new Runnable() {
			@Override
			public void run() {
				LOG.info("Got result from decoder");
				// Ignore results until the KeyAgreementTask is ready
				if (!gotLocalPayload) {
					return;
				}
				if (!gotRemotePayload) {
					gotRemotePayload = true;
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
