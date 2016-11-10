package org.briarproject.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.UiThread;

import org.briarproject.R;
import org.briarproject.android.contactselection.ContactSelectorActivity;
import org.briarproject.android.contactselection.ContactSelectorFragment;
import org.briarproject.android.contactselection.SelectableContactItem;
import org.briarproject.android.sharing.BaseMessageFragment.MessageFragmentListener;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.sync.GroupId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class ShareActivity
		extends ContactSelectorActivity<SelectableContactItem>
		implements MessageFragmentListener {

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);

		if (bundle == null) {
			ContactSelectorFragment contactSelectorFragment =
					ContactSelectorFragment.newInstance(groupId);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragmentContainer, contactSelectorFragment)
					.commit();
		}
	}

	@UiThread
	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		super.contactsSelected(contacts);

		BaseMessageFragment messageFragment = getMessageFragment();
		getSupportFragmentManager().beginTransaction()
				.setCustomAnimations(android.R.anim.fade_in,
						android.R.anim.fade_out,
						android.R.anim.slide_in_left,
						android.R.anim.slide_out_right)
				.replace(R.id.fragmentContainer, messageFragment,
						ContactSelectorFragment.TAG)
				.addToBackStack(null)
				.commit();
	}

	abstract BaseMessageFragment getMessageFragment();

	@UiThread
	@Override
	public boolean onButtonClick(@NotNull String message) {
		share(contacts, message);
		setResult(RESULT_OK);
		supportFinishAfterTransition();
		return true;
	}

	abstract void share(Collection<ContactId> contacts, String msg);

}
