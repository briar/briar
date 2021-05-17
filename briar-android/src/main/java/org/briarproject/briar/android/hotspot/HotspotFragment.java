package org.briarproject.briar.android.hotspot;

import android.os.Bundle;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

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
		// no need to call into the ViewModel here
		connectedButton.setOnClickListener(v -> {
			Fragment f = new WebsiteFragment();
			String tag = WebsiteFragment.TAG;
			showFragment(getParentFragmentManager(), f, tag);
		});
	}

	@Override
	protected Fragment getFirstFragment() {
		return ManualHotspotFragment.newInstance(true);
	}

	@Override
	protected Fragment getSecondFragment() {
		return QrHotspotFragment.newInstance(true);
	}

}
