package org.briarproject.briar.android.login;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.textfield.TextInputEditText;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TelegramLoginPlaceholderFragment extends BaseFragment {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private StartupViewModel viewModel;

	static final String TAG =
			TelegramLoginPlaceholderFragment.class.getName();

	static TelegramLoginPlaceholderFragment newInstance() {
		return new TelegramLoginPlaceholderFragment();
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(requireActivity(),
				viewModelFactory).get(StartupViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_telegram_login_placeholder,
				container, false);
		TextInputEditText identifier =
				v.findViewById(R.id.telegram_login_identifier);
		identifier.setText(viewModel.getTelegramLoginIdentifier());
		identifier.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				viewModel.setTelegramLoginIdentifier(s.toString());
			}
		});
		v.findViewById(R.id.btn_telegram_login_back)
				.setOnClickListener(view -> viewModel.showPasswordFragment());
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
