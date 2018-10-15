package org.briarproject.briar.android.contact;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.view.QrCodeView;

import javax.annotation.Nullable;

import static org.briarproject.briar.android.contact.ContactLinkExchangeActivity.OUR_LINK;
import static org.briarproject.briar.android.keyagreement.QrCodeUtils.createQrCode;

public class ContactQrCodeOutputFragment extends BaseFragment {

	static final String TAG = ContactQrCodeOutputFragment.class.getName();

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		if (getActivity() == null) return null;

		getActivity().setTitle(R.string.show_qr_code_title);

		View v = inflater.inflate(R.layout.fragment_contact_qr_code_output,
				container, false);

		DisplayMetrics dm = getResources().getDisplayMetrics();
		Bitmap qrCode = createQrCode(dm, OUR_LINK);
		QrCodeView qrCodeView = v.findViewById(R.id.qrCodeView);
		qrCodeView.setQrCode(qrCode);

		return v;
	}
}
