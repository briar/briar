package org.briarproject.briar.android.hotspot;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;

import androidx.annotation.Nullable;


@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class HotspotErrorFragment extends BaseFragment {

	public static final String TAG = HotspotErrorFragment.class.getName();

	private static final String ERROR_MSG = "errorMessage";

	public static HotspotErrorFragment newInstance(String message) {
		HotspotErrorFragment f = new HotspotErrorFragment();
		Bundle args = new Bundle();
		args.putString(ERROR_MSG, message);
		f.setArguments(args);
		return f;
	}

	private String errorMessage;

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle args = getArguments();
		if (args == null) throw new AssertionError();
		errorMessage = args.getString(ERROR_MSG);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_hotspot_error, container, false);
		TextView msg = v.findViewById(R.id.errorMessageDetail);
		msg.setText(errorMessage);
		return v;
	}

}
