package org.briarproject.briar.android.hotspot;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static org.briarproject.briar.android.util.UiUtils.showFragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotFragment extends AbstractTabsFragment {

	public final static String TAG = HotspotFragment.class.getName();

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		connectedButton.setOnClickListener(v -> showNextFragment());
		viewModel.getPeerConnectedEvent().observeEvent(getViewLifecycleOwner(),
				this::onPeerConnected);
	}

	@Override
	protected Fragment getFirstFragment() {
		return ManualHotspotFragment.newInstance(true);
	}

	@Override
	protected Fragment getSecondFragment() {
		return QrHotspotFragment.newInstance(true);
	}

	private void onPeerConnected(boolean connected) {
		if (!connected) return;
		Toast.makeText(requireContext(), R.string.hotspot_peer_connected,
				Toast.LENGTH_LONG).show();
	}

	private void showNextFragment() {
		Fragment f = new WebsiteFragment();
		String tag = WebsiteFragment.TAG;
		showFragment(getParentFragmentManager(), f, tag);
	}

}
