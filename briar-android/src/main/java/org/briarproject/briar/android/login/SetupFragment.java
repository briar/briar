package org.briarproject.briar.android.login;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import org.briarproject.briar.R;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.inject.Inject;

import static org.briarproject.briar.android.util.UiUtils.showOnboardingDialog;

abstract class SetupFragment extends BaseFragment implements TextWatcher,
		OnEditorActionListener, OnClickListener {

	@Inject
	SetupController setupController;

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.help_action, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.action_help) {
			showOnboardingDialog(getContext(), getHelpText());
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	protected abstract String getHelpText();

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		// noop
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before,
			int count) {
		// noop
	}

	@Override
	public boolean onEditorAction(TextView textView, int actionId,
			KeyEvent keyEvent) {
		onClick(textView);
		return true;
	}

	@Override
	public void afterTextChanged(Editable editable) {
		// noop
	}

}
