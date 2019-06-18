package org.briarproject.briar.android.contact.add.remote;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.utf8IsTooLong;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class NicknameFragment extends BaseFragment {

	private static final String TAG = NicknameFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private AddContactViewModel viewModel;

	private TextInputLayout contactNameLayout;
	private TextInputEditText contactNameInput;
	private Button addButton;
	private ProgressBar progressBar;

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

		contactNameLayout = v.findViewById(R.id.contactNameLayout);
		contactNameInput = v.findViewById(R.id.contactNameInput);

		addButton = v.findViewById(R.id.addButton);
		addButton.setOnClickListener(view -> onAddButtonClicked());

		progressBar = v.findViewById(R.id.progressBar);

		return v;
	}

	@Nullable
	private String getNicknameOrNull() {
		Editable text = contactNameInput.getText();
		if (text == null || text.toString().trim().length() == 0) {
			contactNameLayout.setError(getString(R.string.nickname_missing));
			contactNameInput.requestFocus();
			return null;
		}
		String name = text.toString().trim();
		if (utf8IsTooLong(name, MAX_AUTHOR_NAME_LENGTH)) {
			contactNameLayout.setError(getString(R.string.name_too_long));
			contactNameInput.requestFocus();
			return null;
		}
		contactNameLayout.setError(null);
		return name;
	}

	private void onAddButtonClicked() {
		String name = getNicknameOrNull();
		if (name == null) return;  // invalid nickname

		addButton.setVisibility(INVISIBLE);
		progressBar.setVisibility(VISIBLE);

		viewModel.getAddContactResult().observe(this, result -> {
			if (result == null) return;
			if (result.hasError()) {
				int stringRes;
				if (result
						.getException() instanceof UnsupportedVersionException) {
					stringRes = R.string.unsupported_link;
				} else {
					stringRes = R.string.adding_contact_error;
				}
				Toast.makeText(getContext(), stringRes, LENGTH_LONG).show();
			} else {
				Intent intent = new Intent(getActivity(),
						PendingContactListActivity.class);
				startActivity(intent);
			}
			finish();
		});
		viewModel.addContact(name);
	}

}
