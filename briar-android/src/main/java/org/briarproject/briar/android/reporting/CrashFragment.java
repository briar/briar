package org.briarproject.briar.android.reporting;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;

import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CrashFragment extends Fragment {

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater
				.inflate(R.layout.fragment_crash, container, false);

		v.findViewById(R.id.acceptButton).setOnClickListener(view ->
				getDevReportActivity().showReportForm(true));
		v.findViewById(R.id.declineButton).setOnClickListener(view ->
				getDevReportActivity().closeReport());

		return v;
	}

	private DevReportActivity getDevReportActivity() {
		return (DevReportActivity) requireNonNull(getActivity());
	}

}
