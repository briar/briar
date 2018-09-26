package org.briarproject.briar.android.contact;

import android.content.ClipboardManager;
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

import static android.content.ClipDescription.MIMETYPE_TEXT_PLAIN;
import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static java.util.Objects.requireNonNull;

@NotNullByDefault
public class ContactLinkInputFragment extends BaseFragment
		implements TextWatcher {

	private ClipboardManager clipboard;
	private EditText linkInput;
	private Button pasteButton;
	private EditText contactNameInput;
	private Button addButton;

	static BaseFragment newInstance(@Nullable String link) {
		BaseFragment f = new ContactLinkInputFragment();
		Bundle bundle = new Bundle();
		bundle.putString("link", link);
		f.setArguments(bundle);
		return f;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {

		getActivity().setTitle(R.string.open_link_title);

		View v = inflater.inflate(R.layout.fragment_contact_link_input,
				container, false);

		clipboard = (ClipboardManager) requireNonNull(
				getContext().getSystemService(CLIPBOARD_SERVICE));

		linkInput = v.findViewById(R.id.linkInput);
		linkInput.addTextChangedListener(this);

		pasteButton = v.findViewById(R.id.pasteButton);
		pasteButton.setOnClickListener(view -> linkInput
				.setText(clipboard.getPrimaryClip().getItemAt(0).getText()));

		contactNameInput = v.findViewById(R.id.contactNameInput);
		contactNameInput.addTextChangedListener(this);

		addButton = v.findViewById(R.id.addButton);
		addButton.setOnClickListener(view -> onAddButtonClicked());

		linkInput.setText(getArguments().getString("link"));

		return v;
	}

	public static final String TAG = ContactLinkInputFragment.class.getName();

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}
	@Override
	public void onResume() {
		super.onResume();
		if (hasLinkInClipboard()) pasteButton.setEnabled(true);
		else pasteButton.setEnabled(false);
	}

	private boolean hasLinkInClipboard() {
		return clipboard.hasPrimaryClip() &&
				clipboard.getPrimaryClip().getDescription()
						.hasMimeType(MIMETYPE_TEXT_PLAIN) &&
				clipboard.getPrimaryClip().getItemCount() > 0;
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
		String link = s.toString().trim();
		return link.matches("^(briar://)?[A-Z2-7]{64}$");
	}

	private void updateAddButtonState() {
		addButton.setEnabled(isBriarLink(linkInput.getText()) &&
				contactNameInput.getText().length() > 0);
	}

	private void onAddButtonClicked() {
		if (getActivity() == null || getContext() == null) return;;

		((ContactLinkInputActivity) getActivity())
				.addFakeRequest(contactNameInput.getText().toString());

		AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.BriarDialogTheme_Neutral);
		builder.setMessage(getString(R.string.add_contact_link_question));
		builder.setPositiveButton(R.string.yes, (dialog, which) -> {
			Intent intent = new Intent(getContext(), NavDrawerActivity.class);
			intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
			finish();
		});
		builder.setNegativeButton(R.string.no, (dialog, which) -> {
			startActivity(
					new Intent(getContext(), ContactLinkOutputActivity.class));
			finish();
		});
		builder.show();
	}


}
