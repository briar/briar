package org.briarproject.briar.android.contact;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.fragment.BaseFragment;

import javax.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;
import static android.support.v4.graphics.drawable.DrawableCompat.setTint;
import static android.support.v4.graphics.drawable.DrawableCompat.wrap;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.util.UiUtils.resolveColorAttribute;

public class ContactNicknameFragment extends BaseFragment {

	static final String TAG = ContactNicknameFragment.class.getName();

	static BaseFragment newInstance(@Nullable String link) {
		BaseFragment f = new ContactNicknameFragment();
		Bundle bundle = new Bundle();
		bundle.putString("link", link);
		f.setArguments(bundle);
		return f;
	}

	private TextInputLayout contactNameLayout;
	private TextInputEditText contactNameInput;

	@Nullable
	private String link;

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
	public View onCreateView(@NonNull LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		if (getActivity() == null || getContext() == null) return null;

		getActivity().setTitle(R.string.add_contact_remotely_title_case);

		link = requireNonNull(getArguments()).getString("link");
		Log.e(TAG, link);

		View v = inflater.inflate(R.layout.fragment_contact_choose_nickname,
				container, false);

		int color =
				resolveColorAttribute(getContext(), R.attr.colorControlNormal);

		Button addButton = v.findViewById(R.id.addButton);
		addButton.setOnClickListener(view -> onAddButtonClicked());

		contactNameLayout = v.findViewById(R.id.contactNameLayout);
		contactNameInput = v.findViewById(R.id.contactNameInput);
		if (SDK_INT < 23) {
			Drawable drawable = wrap(contactNameInput.getCompoundDrawables()[0]);
			setTint(drawable, color);
			contactNameInput.setCompoundDrawables(drawable, null, null, null);
		}

		return v;
	}

	private ContactLinkExchangeActivity getCastActivity() {
		return (ContactLinkExchangeActivity) getActivity();
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
		ContactLinkExchangeActivity activity = getCastActivity();
		if (activity == null || isInputError()) return;

		String name = requireNonNull(contactNameInput.getText()).toString();
		if (link == null) throw new AssertionError();

		activity.addFakeRequest(name, link);

		Intent intent = new Intent(activity, PendingRequestsActivity.class);
		startActivity(intent);
		finish();
	}

}
