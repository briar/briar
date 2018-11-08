package org.briarproject.briar.android.conversation;

import android.arch.lifecycle.ViewModelProvider;
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
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;

public class AliasDialogFragment extends AppCompatDialogFragment {

	final static String TAG = AliasDialogFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ConversationViewModel viewModel;
	private EditText aliasEditText;

	public static AliasDialogFragment newInstance() {
		return new AliasDialogFragment();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setStyle(STYLE_NO_TITLE, R.style.BriarDialogTheme);

		if (getActivity() == null) return;
		((BriarActivity) getActivity()).getActivityComponent().inject(this);
		viewModel = ViewModelProviders.of(getActivity(), viewModelFactory)
				.get(ConversationViewModel.class);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater,
			ViewGroup container, Bundle savedInstanceState) {

		View v = inflater.inflate(R.layout.fragment_alias_dialog, container,
				false);

		aliasEditText = v.findViewById(R.id.aliasEditText);
		Contact contact = requireNonNull(viewModel.getContact().getValue());
		String alias = contact.getAlias();
		aliasEditText.setText(alias);
		if (alias != null) aliasEditText.setSelection(alias.length());

		Button setButton = v.findViewById(R.id.setButton);
		setButton.setOnClickListener(v1 -> {
			viewModel.setContactAlias(aliasEditText.getText().toString());
			getDialog().dismiss();
		});

		Button cancelButton = v.findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(v1 -> getDialog().cancel());

		return v;
	}
}
