package org.briarproject.briar.android.mailbox;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.qrcode.CameraException;
import org.briarproject.briar.android.qrcode.CameraView;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

// TODO have to get camera permission somewhere

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class MailboxPairFragment extends BaseFragment {

	static final String TAG = MailboxPairFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private MailboxPairViewModel viewModel;
	private CameraView cameraView;
	private View statusView;
	private TextView status;

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(MailboxPairViewModel.class);
		viewModel.getState().observeEvent(this, this::onStateChanged);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_mailbox_qr, container, false);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		cameraView = view.findViewById(R.id.camera_view);
		cameraView.setPreviewConsumer(viewModel.getQrCodeDecoder());
		statusView = view.findViewById(R.id.status_container);
		status = view.findViewById(R.id.connect_status);

		requireActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);
	}

	private void onStateChanged(MailboxPairViewModel.State state) {
		if (state == MailboxPairViewModel.State.QRCODE_VALID ||
				state == MailboxPairViewModel.State.QRCODE_INVALID) {
			tryStopCamera();
			cameraView.setVisibility(INVISIBLE);
			statusView.setVisibility(VISIBLE);
			CharSequence text = "That's not a valid Mailbox QR code.";
			if (state == MailboxPairViewModel.State.QRCODE_VALID) {
				String fmt =
						"curl -v --socks5-hostname 127.0.0.1:9050" +
								" -H \"Authorization: Bearer %s\"" +
								" -X PUT http://%s.onion/setup";
				text = String.format(fmt, viewModel.getSetupToken(),
						viewModel.getOnionAddress());
			}
			status.setText(text);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		try {
			cameraView.start();
		} catch (CameraException e) {
			logCameraExceptionAndFinish(e);
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		tryStopCamera();
	}

	void tryStopCamera() {
		try {
			cameraView.stop();
		} catch (CameraException e) {
			logCameraExceptionAndFinish(e);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@UiThread
	private void logCameraExceptionAndFinish(CameraException e) {
		logException(LOG, WARNING, e);
		Toast.makeText(getActivity(), R.string.camera_error,
				LENGTH_LONG).show();
		finish();
	}

	@Override
	protected void finish() {
		requireActivity().getSupportFragmentManager().popBackStack();
	}

}
