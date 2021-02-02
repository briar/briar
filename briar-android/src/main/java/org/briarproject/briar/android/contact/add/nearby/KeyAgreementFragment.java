package org.briarproject.briar.android.contact.add.nearby;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.ContactExchangeStarted;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.Failed;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.KeyAgreementStarted;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.KeyAgreementWaiting;
import org.briarproject.briar.android.contact.add.nearby.ContactAddingState.QrCodeScanned;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.QrCodeView;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class KeyAgreementFragment extends BaseFragment
		implements QrCodeView.FullscreenListener {

	static final String TAG = KeyAgreementFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ContactExchangeViewModel viewModel;
	private CameraView cameraView;
	private LinearLayout cameraOverlay;
	private View statusView;
	private QrCodeView qrCodeView;
	private TextView status;

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

		viewModel.getState().observe(getViewLifecycleOwner(),
				this::onContactAddingStateChanged);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		requireActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);
		cameraView.setPreviewConsumer(viewModel.qrCodeDecoder);
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
		try {
			cameraView.stop();
		} catch (CameraException e) {
			logCameraExceptionAndFinish(e);
		}
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

	@UiThread
	private void onContactAddingStateChanged(ContactAddingState state) {
		if (state instanceof ContactAddingState.KeyAgreementListening) {
			Bitmap qrCode =
					((ContactAddingState.KeyAgreementListening) state).qrCode;
			qrCodeView.setQrCode(qrCode);
		} else if (state instanceof QrCodeScanned) {
			try {
				cameraView.stop();
			} catch (CameraException e) {
				logCameraExceptionAndFinish(e);
			}
			cameraView.setVisibility(INVISIBLE);
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.connecting_to_device);
		} else if (state instanceof KeyAgreementWaiting) {
			status.setText(R.string.waiting_for_contact_to_scan);
		} else if (state instanceof KeyAgreementStarted) {
			qrCodeView.setVisibility(INVISIBLE);
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.authenticating_with_device);
		} else if (state instanceof ContactExchangeStarted) {
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.exchanging_contact_details);
		} else if (state instanceof Failed) {
			// the activity will replace this fragment with an error fragment
			statusView.setVisibility(INVISIBLE);
			cameraView.setVisibility(INVISIBLE);
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
