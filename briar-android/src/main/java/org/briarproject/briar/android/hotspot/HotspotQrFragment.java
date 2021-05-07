package org.briarproject.briar.android.hotspot;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import static org.briarproject.briar.android.AppModule.getAndroidComponent;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotQrFragment extends Fragment {

	public final static String TAG = HotspotQrFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private HotspotViewModel viewModel;

	static HotspotQrFragment newInstance(boolean forWifiConnect) {
		HotspotQrFragment f = new HotspotQrFragment();
		Bundle bundle = new Bundle();
		bundle.putBoolean("forWifiConnect", forWifiConnect);
		f.setArguments(bundle);
		return f;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		getAndroidComponent(requireContext()).inject(this);
		viewModel = new ViewModelProvider(requireActivity(), viewModelFactory)
				.get(HotspotViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_hotspot_qr, container, false);

		TextView qrIntroView = v.findViewById(R.id.qrIntroView);
		ImageView qrCodeView = v.findViewById(R.id.qrCodeView);

		if (requireArguments().getBoolean("forWifiConnect")) {
			qrIntroView.setText(R.string.hotspot_qr_wifi);
			// TODO observe state in ViewModel and get QR code from there
		} else {
			qrIntroView.setText(R.string.hotspot_qr_site);
			// TODO observe state in ViewModel and get QR code from there
		}
		return v;
	}

}
