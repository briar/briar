package org.briarproject.android;

import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.contact.ConversationActivity;
import org.briarproject.android.forum.AvailableForumsActivity;
import org.briarproject.android.forum.CreateForumActivity;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.forum.ReadForumPostActivity;
import org.briarproject.android.forum.ShareForumActivity;
import org.briarproject.android.forum.WriteForumPostActivity;
import org.briarproject.android.fragment.SettingsFragment;
import org.briarproject.android.identity.CreateIdentityActivity;
import org.briarproject.android.invitation.AddContactActivity;
import org.briarproject.android.panic.PanicPreferencesActivity;
import org.briarproject.android.panic.PanicResponderActivity;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.data.DataModule;
import org.briarproject.db.DatabaseModule;
import org.briarproject.event.EventModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.identity.IdentityModule;
import org.briarproject.invitation.InvitationModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.messaging.MessagingModule;
import org.briarproject.plugins.AndroidPluginsModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.reliability.ReliabilityModule;
import org.briarproject.settings.SettingsModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.system.AndroidSystemModule;
import org.briarproject.transport.TransportModule;


import javax.inject.Singleton;
import dagger.Component;
@Singleton
@Component(
		modules = {AppModule.class, AndroidModule.class, DatabaseModule.class,
				CryptoModule.class, LifecycleModule.class,
				ReliabilityModule.class, MessagingModule.class,
				InvitationModule.class, ForumModule.class, IdentityModule.class,
				EventModule.class, DataModule.class, ContactModule.class,
				AndroidSystemModule.class, AndroidPluginsModule.class,
				PropertiesModule.class, TransportModule.class, SyncModule.class,
				SettingsModule.class})
public interface AndroidComponent {
	void inject(SplashScreenActivity activity);
	void inject(SetupActivity activity);
	void inject(NavDrawerActivity activity);
	void inject(PasswordActivity activity);
	void inject(BriarService activity);
	void inject(PanicResponderActivity activity);
	void inject(PanicPreferencesActivity activity);
	void inject(AddContactActivity activity);
	void inject(ConversationActivity activity);
	void inject(CreateIdentityActivity activity);
	void inject(TestingActivity activity);
	void inject(AvailableForumsActivity activity);
	void inject(WriteForumPostActivity activity);
	void inject(CreateForumActivity activity);
	void inject(ShareForumActivity activity);
	void inject(ReadForumPostActivity activity);
	void inject(ForumActivity activity);
	void inject(ContactListFragment fragment);
	void inject(SettingsFragment fragment);
	void inject(ForumListFragment fragment);

}
