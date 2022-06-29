package org.briarproject.briar.android.settings;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AboutFragment extends Fragment {

	private TextView briarVersion;
	private TextView briarWebsite;
	private TextView briarSourceCode;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_about, container,
				false);
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.about_title);
		briarVersion = requireActivity().findViewById(R.id.BriarVersion);
		briarVersion.setText(getString(R.string.briar_version) + " " +
				BuildConfig.VERSION_NAME);
		briarWebsite = requireActivity().findViewById(R.id.BriarWebsite);
		briarSourceCode = requireActivity().findViewById(R.id.BriarSourceCode);
		briarWebsite.setMovementMethod(LinkMovementMethod.getInstance());
		briarSourceCode.setMovementMethod(LinkMovementMethod.getInstance());
	}

}