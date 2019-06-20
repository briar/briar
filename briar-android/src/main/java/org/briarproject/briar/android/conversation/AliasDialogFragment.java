package org.briarproject.briar.android.conversation;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_AUTHOR_NAME_LENGTH;
import static org.briarproject.bramble.util.StringUtils.toUtf8;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AliasDialogFragment extends AppCompatDialogFragment {

	final static String TAG = AliasDialogFragment.class.getName();

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ConversationViewModel viewModel;
	private TextInputLayout aliasEditLayout;
	private EditText aliasEditText;

	public static AliasDialogFragment newInstance() {
		return new AliasDialogFragment();
	}

	@Override
	public void onAttach(Context ctx) {
		super.onAttach(ctx);
		((BaseActivity) requireActivity()).getActivityComponent().inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setStyle(STYLE_NO_TITLE, R.style.BriarDialogTheme);

		viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)
				.get(ConversationViewModel.class);
	}

	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_alias_dialog, container,
				false);

		aliasEditLayout = v.findViewById(R.id.aliasEditLayout);
		aliasEditText = v.findViewById(R.id.aliasEditText);
		Contact contact = requireNonNull(viewModel.getContact().getValue());
		String alias = contact.getAlias();
		aliasEditText.setText(alias);
		if (alias != null) aliasEditText.setSelection(alias.length());

		Button setButton = v.findViewById(R.id.setButton);
		setButton.setOnClickListener(v1 -> onSetButtonClicked());

		Button cancelButton = v.findViewById(R.id.cancelButton);
		cancelButton.setOnClickListener(v1 -> getDialog().cancel());

		return v;
	}

	private void onSetButtonClicked() {
		String alias = aliasEditText.getText().toString().trim();
		if (toUtf8(alias).length > MAX_AUTHOR_NAME_LENGTH) {
			aliasEditLayout.setError(getString(R.string.name_too_long));
		} else {
			viewModel.setContactAlias(alias);
			getDialog().dismiss();
		}
	}

}
