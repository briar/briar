package org.briarproject.android.privategroup.creation;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.fragment.BaseFragment;

import static org.briarproject.api.privategroup.PrivateGroupConstants.MAX_GROUP_NAME_LENGTH;

public class CreateGroupFragment extends BaseFragment {

	public final static String TAG = CreateGroupFragment.class.getName();

	private CreateGroupListener listener;
	private EditText name;
	private Button button;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		listener = (CreateGroupListener) context;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		// inflate view
		View v = inflater.inflate(R.layout.fragment_create_group, container,
				false);
		name = (EditText) v.findViewById(R.id.name);
		name.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				validateName();
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		button = (Button) v.findViewById(R.id.button);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				listener.hideSoftKeyboard(name);
				listener.onGroupNameChosen(name.getText().toString());
			}
		});

		return v;
	}

	@Override
	public void onStart() {
		super.onStart();
		listener.showSoftKeyboard(name);
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	private void validateName() {
		String name = this.name.getText().toString();
		if (name.length() < 1 || name.length() > MAX_GROUP_NAME_LENGTH)
			button.setEnabled(false);
		else if(!button.isEnabled())
			button.setEnabled(true);
	}

}
