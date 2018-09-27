package org.briarproject.briar.android.contact;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.zxing.Result;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.keyagreement.CameraException;
import org.briarproject.briar.android.keyagreement.CameraView;
import org.briarproject.briar.android.keyagreement.QrCodeDecoder;
import org.briarproject.briar.android.util.UiUtils;

import javax.annotation.Nullable;

import static android.Manifest.permission.CAMERA;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_PERMISSION_CAMERA;

@NotNullByDefault
public class ContactQrCodeInputFragment extends BaseFragment
		implements QrCodeDecoder.ResultCallback {

	private CameraView cameraView;

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getActivity().setRequestedOrientation(SCREEN_ORIENTATION_NOSENSOR);
		cameraView.setPreviewConsumer(new QrCodeDecoder(this));
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		getActivity().setTitle("Scan QR Code");

		View v = inflater.inflate(R.layout.fragment_contact_qr_code_input,
				container, false);

		cameraView = v.findViewById(R.id.camera_view);


//		Button enterLinkButton = v.findViewById(R.id.enterLinkButton);
//		enterLinkButton.setOnClickListener(view ->
//				((ContactInviteInputActivity) getActivity()).showLink());

		return v;
	}

	public static final String TAG = ContactQrCodeInputFragment.class.getName();

	@Override
	public void onStart() {
		super.onStart();
		if (checkPermissions()) {
			startCamera();
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		try {
			cameraView.stop();
		} catch (CameraException e) {
			e.printStackTrace();
		}
	}

	private void startCamera() {
		try {
			cameraView.start();
		} catch (CameraException e) {
			e.printStackTrace();
			Toast.makeText(getContext(), "Camera Error", LENGTH_SHORT)
					.show();
		}
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	private boolean checkPermissions() {
		if (ActivityCompat.checkSelfPermission(getContext(), CAMERA) !=
				PERMISSION_GRANTED) {
			// Should we show an explanation?
			if (shouldShowRequestPermissionRationale(CAMERA)) {
				DialogInterface.OnClickListener continueListener =
						(dialog, which) -> requestPermission();
				AlertDialog.Builder
						builder = new AlertDialog.Builder(getContext(), R.style.BriarDialogTheme);
				builder.setTitle(R.string.permission_camera_title);
				builder.setMessage(R.string.permission_camera_request_body);
				builder.setNeutralButton(R.string.continue_button,
						continueListener);
				builder.show();
			} else {
				requestPermission();
			}
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
			@NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == REQUEST_PERMISSION_CAMERA) {
			// If request is cancelled, the result arrays are empty.
			if (grantResults.length > 0 &&
					grantResults[0] == PERMISSION_GRANTED) {
				startCamera();
			} else {
				if (!shouldShowRequestPermissionRationale(CAMERA)) {
					// The user has permanently denied the request
					AlertDialog.Builder
							builder = new AlertDialog.Builder(getContext(),
							R.style.BriarDialogTheme);
					builder.setTitle(R.string.permission_camera_title);
					builder.setMessage(R.string.permission_camera_denied_body);
					builder.setPositiveButton(R.string.ok,
							UiUtils.getGoToSettingsListener(getContext()));
					builder.setNegativeButton(R.string.cancel,
							(dialog, which) -> showLink(null));
					builder.show();
				} else {
					Toast.makeText(getContext(),
							R.string.permission_camera_denied_toast,
							LENGTH_LONG).show();
					showLink(null);
				}
			}
		}
	}

	private void requestPermission() {
		requestPermissions(new String[] {CAMERA}, REQUEST_PERMISSION_CAMERA);
	}

	private void showLink(@Nullable String link) {
		if (getActivity() != null)
			((ContactInviteInputActivity) getActivity()).showLink(link);
	}

	@Override
	public void handleResult(Result result) {
		Log.e("TEST", result.toString());
		if (getActivity() != null &&
				((ContactInviteInputActivity) getActivity())
						.isBriarLink(result.getText())) {
			showLink(result.getText());
		}
	}

}
