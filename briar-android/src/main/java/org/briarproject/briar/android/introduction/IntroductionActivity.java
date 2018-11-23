package org.briarproject.briar.android.introduction;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class IntroductionActivity extends BriarActivity
		implements BaseFragmentListener {

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();
		int id = intent.getIntExtra(CONTACT_ID, -1);
		if (id == -1) throw new IllegalStateException("No ContactId");
		ContactId contactId = new ContactId(id);

		setContentView(R.layout.activity_fragment_container);

		if (savedInstanceState == null) {
			showInitialFragment(ContactChooserFragment.newInstance(contactId));
		}
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

}
