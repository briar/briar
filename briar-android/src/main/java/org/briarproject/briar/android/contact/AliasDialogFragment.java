package org.briarproject.briar.android.contact;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;

public class AliasDialogFragment extends AppCompatDialogFragment {

	final static String TAG = AliasDialogFragment.class.getName();

	private ConversationViewModel viewModel;
	private ContactId contactId;
	private EditText aliasEditText;

	public static AliasDialogFragment newInstance(ContactId id) {
		AliasDialogFragment f = new AliasDialogFragment();

		Bundle args = new Bundle();
		args.putInt("contactId", id.getInt());
		f.setArguments(args);

		return f;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (getArguments() == null) throw new IllegalArgumentException();
		int contactIdInt = getArguments().getInt("contactId", -1);
		if (contactIdInt == -1) throw new IllegalArgumentException();
		contactId = new ContactId(contactIdInt);

		setStyle(STYLE_NO_TITLE, R.style.BriarDialogTheme);

		viewModel =
				ViewModelProviders.of(getActivity()).get(ConversationViewModel.class);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_alias_dialog, container,
				false);

		aliasEditText = v.findViewById(R.id.aliasEditText);
		Contact contact = viewModel.getContact().getValue();
		String alias = contact == null ? null : contact.getAlias();
		aliasEditText.setText(alias);
		if (alias != null) aliasEditText.setSelection(alias.length());

		Button setButton = v.findViewById(R.id.setButton);
		setButton.setOnClickListener(v1 -> {
			viewModel.setContactAlias(contactId,
					aliasEditText.getText().toString());
			getDialog().dismiss();
		});

		Button cancelButton = v.findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(v1 -> getDialog().cancel());

		return v;
	}
}
