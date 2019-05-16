package org.briarproject.briar.android.contact.add.remote;

import android.arch.lifecycle.ViewModelProvider;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.PendingContact;
import org.briarproject.bramble.api.contact.PendingContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.view.BriarRecyclerView;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.contact.PendingContactState.FAILED;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class PendingContactListActivity extends BriarActivity
		implements PendingContactListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private PendingContactListViewModel viewModel;
	private PendingContactListAdapter adapter;
	private BriarRecyclerView list;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.list);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		viewModel = ViewModelProviders.of(this, viewModelFactory)
				.get(PendingContactListViewModel.class);
		viewModel.getPendingContacts()
				.observe(this, this::onPendingContactsChanged);

		adapter = new PendingContactListAdapter(this, this, PendingContact.class);
		list = findViewById(R.id.list);
		list.setEmptyText(R.string.no_pending_contacts);
		list.setLayoutManager(new LinearLayoutManager(this));
		list.setAdapter(adapter);
		list.showProgressBar();
	}

	@Override
	public void onStart() {
		super.onStart();
		list.startPeriodicUpdate();
	}

	@Override
	protected void onStop() {
		super.onStop();
		list.stopPeriodicUpdate();
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
	public void onFailedPendingContactRemoved(PendingContact pendingContact) {
		// no need to show warning dialog for failed pending contacts
		if (pendingContact.getState() == FAILED) {
			removePendingContact(pendingContact.getId());
			return;
		}
		// show warning dialog
		OnClickListener removeListener = (dialog, which) ->
				removePendingContact(pendingContact.getId());
		AlertDialog.Builder builder = new AlertDialog.Builder(
				PendingContactListActivity.this, R.style.BriarDialogTheme);
		builder.setTitle(
				getString(R.string.dialog_title_remove_pending_contact));
		builder.setMessage(
				getString(R.string.dialog_message_remove_pending_contact));
		builder.setNegativeButton(R.string.groups_remove, removeListener);
		builder.setPositiveButton(R.string.cancel, null);
		// re-enable remove button when dialog is dismissed/canceled
		builder.setOnDismissListener(dialog -> adapter.notifyDataSetChanged());
		builder.show();
	}

	private void removePendingContact(PendingContactId id) {
		viewModel.removePendingContact(id);
	}

	private void onPendingContactsChanged(Collection<PendingContact> contacts) {
		if (contacts.isEmpty()) {
			if (adapter.isEmpty()) {
				list.showData();  // hides progress bar, shows empty text
			} else {
				// all previous contacts have been removed, so we can finish
				supportFinishAfterTransition();
			}
		} else {
			adapter.setItems(contacts);
		}
	}

}
