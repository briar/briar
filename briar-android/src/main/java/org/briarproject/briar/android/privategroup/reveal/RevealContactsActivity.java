package org.briarproject.briar.android.privategroup.reveal;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
import android.support.v7.app.AlertDialog;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contactselection.ContactSelectorActivity;
import org.briarproject.briar.android.controller.handler.UiExceptionHandler;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;

import java.util.Collection;

import javax.annotation.Nullable;
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
			showInitialFragment(RevealContactsFragment.newInstance(groupId));
		}
	}

	@Override
	@LayoutRes
	protected int getLayout() {
		return R.layout.activity_reveal_contacts;
	}

	@Override
	public void onStart() {
		super.onStart();
		controller.isOnboardingNeeded(
				new UiResultExceptionHandler<Boolean, DbException>(this) {
					@Override
					public void onResultUi(Boolean show) {
						if (show) showOnboardingDialog();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.group_reveal_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_group_reveal_onboarding:
				showOnboardingDialog();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void showOnboardingDialog() {
		new AlertDialog.Builder(this, R.style.OnboardingDialogTheme)
				.setMessage(getString(R.string.groups_reveal_dialog_message))
				.setNeutralButton(R.string.got_it,
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								dialog.cancel();
							}
						})
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						onboardingShown();
					}
				})
				.show();
	}

	private void onboardingShown() {
		controller.onboardingShown(
				new UiExceptionHandler<DbException>(this) {
					@Override
					public void onExceptionUi(DbException exception) {
						handleDbException(exception);
					}
				});
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
						handleDbException(exception);
					}
				});
		supportFinishAfterTransition();
	}

}
