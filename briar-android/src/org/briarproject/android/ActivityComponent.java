package org.briarproject.android;

import android.app.Activity;

import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.AvailableForumsActivity;
import org.briarproject.android.forum.ContactSelectorFragment;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ReadForumPostActivity;
import org.briarproject.android.forum.ShareForumActivity;
import org.briarproject.android.forum.ShareForumMessageFragment;
import org.briarproject.android.forum.WriteForumPostActivity;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;

import javax.inject.Named;

import dagger.Component;

@ActivityScope
@Component(modules = ActivityModule.class,
		dependencies = AndroidComponent.class)
public interface ActivityComponent {

	Activity activity();

	void inject(SplashScreenActivity activity);

	void inject(SetupActivity activity);

	void inject(NavDrawerActivity activity);

	void inject(PasswordActivity activity);

	void inject(PanicResponderActivity activity);

	void inject(PanicPreferencesActivity activity);

	void inject(AddContactActivity activity);

	void inject(KeyAgreementActivity activity);

	void inject(ConversationActivity activity);

	void inject(CreateIdentityActivity activity);

	void inject(AvailableForumsActivity activity);

	void inject(WriteForumPostActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ReadForumPostActivity activity);

	void inject(ForumActivity activity);

	void inject(SettingsActivity activity);

	void inject(IntroductionActivity activity);

	@Named("ContactListFragment")
	BaseFragment newContactListFragment();

	@Named("ForumListFragment")
	BaseFragment newForumListFragment();

	@Named("ChooseIdentityFragment")
	BaseFragment newChooseIdentityFragment();

	@Named("ShowQrCodeFragment")
	BaseFragment newShowQrCodeFragment();

	@Named("ContactChooserFragment")
	BaseFragment newContactChooserFragment();

	@Named("ContactSelectorFragment")
	ContactSelectorFragment newContactSelectorFragment();

	@Named("ShareForumMessageFragment")
	ShareForumMessageFragment newShareForumMessageFragment();

	@Named("IntroductionMessageFragment")
	IntroductionMessageFragment newIntroductionMessageFragment();
}
