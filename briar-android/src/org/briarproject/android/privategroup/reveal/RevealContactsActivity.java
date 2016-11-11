package org.briarproject.android.privategroup.reveal;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contactselection.ContactSelectorActivity;
import org.briarproject.android.controller.handler.UiExceptionHandler;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.db.DbException;
import org.briarproject.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.api.sync.GroupId;

import java.util.Collection;

import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class RevealContactsActivity extends ContactSelectorActivity
		implements OnClickListener {

	private Button button;

	@Inject
	RevealContactsController controller;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	@SuppressWarnings("ConstantConditions")
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);

		button = (Button) findViewById(R.id.revealButton);
		button.setOnClickListener(this);
		button.setEnabled(false);

		if (bundle == null) {
			RevealContactsFragment fragment =
					RevealContactsFragment.newInstance(groupId);
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.fragmentContainer, fragment)
					.commit();
		}
	}

	@Override
	@LayoutRes
	protected int getLayout() {
		return R.layout.activity_reveal_contacts;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		super.contactsSelected(contacts);
		button.setEnabled(!contacts.isEmpty());
	}

	@Override
	public void onClick(View v) {
		controller.reveal(groupId, contacts,
				new UiExceptionHandler<DbException>(this) {
					@Override
					public void onExceptionUi(DbException exception) {
						// TODO proper error handling
						finish();
					}
				});
		supportFinishAfterTransition();
	}

}
