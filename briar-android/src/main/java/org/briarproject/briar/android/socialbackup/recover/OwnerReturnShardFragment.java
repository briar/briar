package org.briarproject.briar.android.socialbackup.recover;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.QrCodeView;
import org.briarproject.briar.api.socialbackup.recovery.SecretOwnerTask;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.lifecycle.ViewModelProvider;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.LinearLayout.HORIZONTAL;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class OwnerReturnShardFragment extends BaseFragment
		implements QrCodeView.FullscreenListener {

	public static final String TAG = OwnerReturnShardFragment.class.getName();

	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private OwnerReturnShardViewModel viewModel;
	private LinearLayout cameraOverlay;
	private View statusView;
	private QrCodeView qrCodeView;
	private TextView status;

	public static OwnerReturnShardFragment newInstance() {
		Bundle args = new Bundle();
		OwnerReturnShardFragment fragment = new OwnerReturnShardFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(OwnerReturnShardViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_recovery_owner_qr, container,
				false);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		cameraOverlay = view.findViewById(R.id.camera_overlay);
		statusView = view.findViewById(R.id.status_container);
		status = view.findViewById(R.id.connect_status);
		qrCodeView = view.findViewById(R.id.qr_code_view);
		qrCodeView.setFullscreenListener(this);

		viewModel.getState().observe(getViewLifecycleOwner(),
				this::onReturnShardStateChanged);
		Bitmap qrCodeBitmap = viewModel.getQrCodeBitmap();
		if (qrCodeBitmap != null) {
			qrCodeView.setQrCode(qrCodeBitmap);
		}
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		requireActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);
	}

	@Override
	public void onDestroy() {
		requireActivity()
				.setRequestedOrientation(SCREEN_ORIENTATION_UNSPECIFIED);
		super.onDestroy();
	}

	@Override
	public void setFullscreen(boolean fullscreen) {
		LinearLayout.LayoutParams statusParams, qrCodeParams;
		if (fullscreen) {
			// Grow the QR code view to fill its parent
			statusParams = new LinearLayout.LayoutParams(0, 0, 0f);
			qrCodeParams =
					new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT,
							1f);
		} else {
			// Shrink the QR code view to fill half its parent
			if (cameraOverlay.getOrientation() == HORIZONTAL) {
				statusParams =
						new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
				qrCodeParams =
						new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f);
			} else {
				statusParams =
						new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f);
				qrCodeParams =
						new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f);
			}
		}
		statusView.setLayoutParams(statusParams);
		qrCodeView.setLayoutParams(qrCodeParams);
		cameraOverlay.invalidate();
	}

	@UiThread
	private void onReturnShardStateChanged(
			@Nullable SecretOwnerTask.State state) {
		if (state instanceof SecretOwnerTask.State.Listening) {
			Bitmap qrCode = viewModel.getQrCodeBitmap();
			qrCodeView.setQrCode(qrCode);
		} else if (state instanceof SecretOwnerTask.State.ReceivingShard) {
			statusView.setVisibility(VISIBLE);
			status.setText(R.string.connecting_to_device);
		} else if (state instanceof SecretOwnerTask.State.SendingAck) {
			status.setText(R.string.waiting_for_contact_to_scan);
		} else if (state instanceof SecretOwnerTask.State.Success) {
			status.setText("Success");
		} else if (state instanceof SecretOwnerTask.State.Failure) {
			// the activity will replace this fragment with an error fragment
			statusView.setVisibility(INVISIBLE);
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	protected void finish() {
		requireActivity().getSupportFragmentManager().popBackStack();
	}

}
