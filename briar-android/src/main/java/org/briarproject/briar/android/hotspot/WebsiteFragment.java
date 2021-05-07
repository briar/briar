package org.briarproject.briar.android.hotspot;

import android.os.Bundle;
import android.view.View;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import static android.view.View.INVISIBLE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class WebsiteFragment extends AbstractTabsFragment {

	public final static String TAG = WebsiteFragment.class.getName();

	@Override
	public void onViewCreated(@NonNull View view,
			@Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		connectedButton.setVisibility(INVISIBLE);
	}

	@Override
	protected Fragment getFirstFragment() {
		return HotspotManualFragment.newInstance(false);
	}

	@Override
	protected Fragment getSecondFragment() {
		return HotspotQrFragment.newInstance(false);
	}

}
