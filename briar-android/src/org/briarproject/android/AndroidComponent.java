package org.briarproject.android;

import org.briarproject.CoreEagerSingletons;
import org.briarproject.CoreModule;
import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.AvailableForumsActivity;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.forum.ReadForumPostActivity;
import org.briarproject.android.forum.ShareForumActivity;
import org.briarproject.android.forum.WriteForumPostActivity;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionActivity;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.keyagreement.ChooseIdentityFragment;
import org.briarproject.android.keyagreement.KeyAgreementActivity;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;
import org.briarproject.plugins.AndroidPluginsModule;
import org.briarproject.system.AndroidSystemModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
		CoreModule.class,
		AppModule.class,
		AndroidPluginsModule.class,
		AndroidSystemModule.class
})
public interface AndroidComponent extends CoreEagerSingletons {

	void inject(SplashScreenActivity activity);

	void inject(SetupActivity activity);

	void inject(NavDrawerActivity activity);

	void inject(PasswordActivity activity);

	void inject(BriarService activity);

	void inject(PanicResponderActivity activity);

	void inject(PanicPreferencesActivity activity);

	void inject(AddContactActivity activity);

	void inject(KeyAgreementActivity activity);

	void inject(ConversationActivity activity);

	void inject(CreateIdentityActivity activity);

	void inject(TestingActivity activity);

	void inject(AvailableForumsActivity activity);

	void inject(WriteForumPostActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ReadForumPostActivity activity);

	void inject(ForumActivity activity);

	void inject(SettingsActivity activity);

	void inject(ContactListFragment fragment);

	void inject(ForumListFragment fragment);

	void inject(ChooseIdentityFragment fragment);

	void inject(ShowQrCodeFragment fragment);

	void inject(IntroductionActivity activity);

	void inject(ContactChooserFragment fragment);

	void inject(IntroductionMessageFragment fragment);

	// Eager singleton load
	void inject(AppModule.EagerSingletons init);
}
