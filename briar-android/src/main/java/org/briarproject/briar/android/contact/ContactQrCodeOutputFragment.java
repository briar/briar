package org.briarproject.briar.android.contact;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.QrCodeView;

import javax.annotation.Nullable;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.bramble.util.StringUtils.getRandomBase32String;
import static org.briarproject.briar.android.keyagreement.QrCodeUtils.createQrCode;

@NotNullByDefault
public class ContactQrCodeOutputFragment extends BaseFragment
		implements QrCodeView.FullscreenListener {

	private View linkIntro;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		getActivity().setTitle(R.string.send_code_title);

		View v = inflater.inflate(R.layout.fragment_contact_qr_code_output,
				container, false);
		linkIntro = v.findViewById(R.id.linkIntro);

		String link = "briar://" + getRandomBase32String(64);
		DisplayMetrics dm = getResources().getDisplayMetrics();
		Bitmap qrCode = createQrCode(dm, link);
		QrCodeView qrCodeView = v.findViewById(R.id.qrCodeView);
		qrCodeView.setQrCode(qrCode);
		qrCodeView.setFullscreenListener(this);

		Button showLinkButton = v.findViewById(R.id.showLinkButton);
		showLinkButton.setOnClickListener(
				view -> ((ContactLinkOutputActivity) getActivity()).showLink());

		return v;
	}

	public static final String TAG = ContactQrCodeOutputFragment.class.getName();

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void setFullscreen(boolean fullscreen) {
		linkIntro.setVisibility(fullscreen ? GONE : VISIBLE);
	}

}
