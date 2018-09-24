package org.briarproject.briar.android.contact.add.remote;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.UiThread;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class NicknameFragment extends BaseFragment {

	private static final String TAG = NicknameFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private AddContactViewModel viewModel;

	private TextInputLayout contactNameLayout;
	private TextInputEditText contactNameInput;

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
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		if (getActivity() == null || getContext() == null) return null;

		View v = inflater.inflate(R.layout.fragment_nickname,
				container, false);

		viewModel = ViewModelProviders.of(getActivity(), viewModelFactory)
				.get(AddContactViewModel.class);

		Button addButton = v.findViewById(R.id.addButton);
		addButton.setOnClickListener(view -> onAddButtonClicked());

		contactNameLayout = v.findViewById(R.id.contactNameLayout);
		contactNameInput = v.findViewById(R.id.contactNameInput);

		return v;
	}

	@MainThread
	@UiThread
	private boolean isInputError() {
		boolean validContactName = contactNameInput.getText() != null &&
				contactNameInput.getText().toString().trim().length() > 0;
		if (!validContactName) {
			contactNameLayout.setError(getString(R.string.nickname_missing));
			contactNameInput.requestFocus();
			return true;
		} else contactNameLayout.setError(null);
		return false;
	}

	private void onAddButtonClicked() {
		if (isInputError()) return;

		String name = requireNonNull(contactNameInput.getText()).toString();
		viewModel.addContact(name);

		Intent intent =
				new Intent(getActivity(), PendingRequestsActivity.class);
		startActivity(intent);
		finish();
	}

}
