package org.briarproject.briar.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;

import javax.annotation.Nullable;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

@NotNullByDefault
public class ContactAliasInputFragment extends BaseFragment
		implements TextWatcher {

	private EditText contactNameInput;
	private Button addButton;

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		getActivity().setTitle("Enter Contact Name");

		View v = inflater.inflate(R.layout.fragment_contact_alias_input,
				container, false);

		contactNameInput = v.findViewById(R.id.contactNameInput);
		contactNameInput.addTextChangedListener(this);

		addButton = v.findViewById(R.id.addButton);
		addButton.setOnClickListener(view -> onAddButtonClicked());

		return v;
	}

	public static final String TAG = ContactAliasInputFragment.class.getName();

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before,
			int count) {
		updateAddButtonState();
	}

	@Override
	public void afterTextChanged(Editable s) {
	}

	private boolean isBriarLink(CharSequence s) {
		return getActivity() != null &&
				((ContactInviteInputActivity) getActivity()).isBriarLink(s);
	}

	private void updateAddButtonState() {
		addButton.setEnabled(contactNameInput.getText().length() > 0);
	}

	private void onAddButtonClicked() {
		if (getActivity() == null || getContext() == null) return;;

		((ContactInviteInputActivity) getActivity())
				.addFakeRequest(contactNameInput.getText().toString());

		AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.BriarDialogTheme_Neutral);
		builder.setTitle("Contact requested");
		builder.setMessage(getString(R.string.add_contact_link_question));
		builder.setPositiveButton(R.string.yes, (dialog, which) -> {
			Intent intent = new Intent(getContext(), NavDrawerActivity.class);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
		});
		builder.setNegativeButton(R.string.no, (dialog, which) -> {
			startActivity(
					new Intent(getContext(), ContactInviteOutputActivity.class));
			finish();
		});
		builder.show();
	}


}
