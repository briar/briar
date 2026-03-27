package org.briarproject.briar.android.login;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.annotation.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TelegramLoginPlaceholderFragment extends BaseFragment {

	static final String TAG =
			TelegramLoginPlaceholderFragment.class.getName();

	static TelegramLoginPlaceholderFragment newInstance() {
		return new TelegramLoginPlaceholderFragment();
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_telegram_login_placeholder,
				container, false);
		v.findViewById(R.id.btn_telegram_login_back)
				.setOnClickListener(view -> getParentFragmentManager().popBackStack());
		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		requireActivity().setTitle(R.string.telegram_connector_login_title);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}
}
