package org.briarproject.briar.android.sharing;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.UiThread;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.contactselection.ContactSelectorActivity;
import org.briarproject.briar.android.sharing.BaseMessageFragment.MessageFragmentListener;

import java.util.Collection;

import javax.annotation.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public abstract class ShareActivity extends ContactSelectorActivity
		implements MessageFragmentListener {

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId");
		groupId = new GroupId(b);
	}

	@UiThread
	@Override
	public void contactsSelected(Collection<ContactId> contacts) {
		super.contactsSelected(contacts);
		showNextFragment(getMessageFragment());
	}

	abstract BaseMessageFragment getMessageFragment();

	@UiThread
	@Override
	public boolean onButtonClick(String message) {
		share(contacts, message);
		setResult(RESULT_OK);
		supportFinishAfterTransition();
		return true;
	}

	abstract void share(Collection<ContactId> contacts, String msg);

}
