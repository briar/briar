package org.briarproject.briar.android.activity;

import android.app.Activity;

import org.briarproject.briar.android.AndroidComponent;
import org.briarproject.briar.android.StartupFailureActivity;
import org.briarproject.briar.android.account.AuthorNameFragment;
import org.briarproject.briar.android.account.DozeFragment;
import org.briarproject.briar.android.account.SetPasswordFragment;
import org.briarproject.briar.android.account.SetupActivity;
import org.briarproject.briar.android.account.UnlockActivity;
import org.briarproject.briar.android.blog.BlogActivity;
import org.briarproject.briar.android.blog.BlogFragment;
import org.briarproject.briar.android.blog.BlogModule;
import org.briarproject.briar.android.blog.BlogPostFragment;
import org.briarproject.briar.android.blog.FeedFragment;
import org.briarproject.briar.android.blog.FeedPostFragment;
import org.briarproject.briar.android.blog.ReblogActivity;
import org.briarproject.briar.android.blog.ReblogFragment;
import org.briarproject.briar.android.blog.RssFeedImportActivity;
import org.briarproject.briar.android.blog.RssFeedManageActivity;
import org.briarproject.briar.android.blog.WriteBlogPostActivity;
import org.briarproject.briar.android.contact.ContactListFragment;
import org.briarproject.briar.android.contact.add.remote.AddContactActivity;
import org.briarproject.briar.android.contact.add.remote.LinkExchangeFragment;
import org.briarproject.briar.android.contact.add.remote.NicknameFragment;
import org.briarproject.briar.android.contact.add.remote.PendingContactListActivity;
import org.briarproject.briar.android.conversation.AliasDialogFragment;
import org.briarproject.briar.android.conversation.ConversationActivity;
import org.briarproject.briar.android.conversation.ImageActivity;
import org.briarproject.briar.android.conversation.ImageFragment;
import org.briarproject.briar.android.forum.CreateForumActivity;
import org.briarproject.briar.android.forum.ForumActivity;
import org.briarproject.briar.android.forum.ForumListFragment;
import org.briarproject.briar.android.fragment.ScreenFilterDialogFragment;
import org.briarproject.briar.android.introduction.ContactChooserFragment;
import org.briarproject.briar.android.introduction.IntroductionActivity;
import org.briarproject.briar.android.introduction.IntroductionMessageFragment;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeActivity;
import org.briarproject.briar.android.contact.add.nearby.ContactExchangeErrorFragment;
import org.briarproject.briar.android.contact.add.nearby.KeyAgreementActivity;
import org.briarproject.briar.android.contact.add.nearby.KeyAgreementFragment;
import org.briarproject.briar.android.login.ChangePasswordActivity;
import org.briarproject.briar.android.login.OpenDatabaseFragment;
import org.briarproject.briar.android.login.PasswordFragment;
import org.briarproject.briar.android.login.StartupActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.android.navdrawer.TransportsActivity;
import org.briarproject.briar.android.panic.PanicPreferencesActivity;
import org.briarproject.briar.android.panic.PanicResponderActivity;
import org.briarproject.briar.android.privategroup.conversation.GroupActivity;
import org.briarproject.briar.android.privategroup.creation.CreateGroupActivity;
import org.briarproject.briar.android.privategroup.creation.CreateGroupFragment;
import org.briarproject.briar.android.privategroup.creation.CreateGroupModule;
import org.briarproject.briar.android.privategroup.creation.GroupInviteActivity;
import org.briarproject.briar.android.privategroup.creation.GroupInviteFragment;
import org.briarproject.briar.android.privategroup.invitation.GroupInvitationActivity;
import org.briarproject.briar.android.privategroup.invitation.GroupInvitationModule;
import org.briarproject.briar.android.privategroup.list.GroupListFragment;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberListActivity;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberModule;
import org.briarproject.briar.android.privategroup.reveal.GroupRevealModule;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsActivity;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsFragment;
import org.briarproject.briar.android.reporting.CrashFragment;
import org.briarproject.briar.android.reporting.CrashReportActivity;
import org.briarproject.briar.android.reporting.ReportFormFragment;
import org.briarproject.briar.android.settings.ConfirmAvatarDialogFragment;
import org.briarproject.briar.android.settings.SettingsActivity;
import org.briarproject.briar.android.settings.SettingsFragment;
import org.briarproject.briar.android.sharing.BlogInvitationActivity;
import org.briarproject.briar.android.sharing.BlogSharingStatusActivity;
import org.briarproject.briar.android.sharing.ForumInvitationActivity;
import org.briarproject.briar.android.sharing.ForumSharingStatusActivity;
import org.briarproject.briar.android.sharing.ShareBlogActivity;
import org.briarproject.briar.android.sharing.ShareBlogFragment;
import org.briarproject.briar.android.sharing.ShareForumActivity;
import org.briarproject.briar.android.sharing.ShareForumFragment;
import org.briarproject.briar.android.sharing.SharingModule;
import org.briarproject.briar.android.splash.SplashScreenActivity;
import org.briarproject.briar.android.test.TestDataActivity;

import dagger.Component;

@ActivityScope
@Component(modules = {
		ActivityModule.class,
		BlogModule.class,
		CreateGroupModule.class,
		GroupInvitationModule.class,
		GroupMemberModule.class,
		GroupRevealModule.class,
		SharingModule.SharingLegacyModule.class
}, dependencies = AndroidComponent.class)
public interface ActivityComponent {

	Activity activity();

	void inject(SplashScreenActivity activity);

	void inject(StartupActivity activity);

	void inject(SetupActivity activity);

	void inject(NavDrawerActivity activity);

	void inject(PanicResponderActivity activity);

	void inject(PanicPreferencesActivity activity);

	void inject(ContactExchangeActivity activity);

	void inject(KeyAgreementActivity activity);

	void inject(ConversationActivity activity);

	void inject(ImageActivity activity);

	void inject(ForumInvitationActivity activity);

	void inject(BlogInvitationActivity activity);

	void inject(CreateGroupActivity activity);

	void inject(GroupActivity activity);

	void inject(GroupInviteActivity activity);

	void inject(GroupInvitationActivity activity);

	void inject(GroupMemberListActivity activity);

	void inject(RevealContactsActivity activity);

	void inject(CreateForumActivity activity);

	void inject(ShareForumActivity activity);

	void inject(ShareBlogActivity activity);

	void inject(ForumSharingStatusActivity activity);

	void inject(BlogSharingStatusActivity activity);

	void inject(ForumActivity activity);

	void inject(BlogActivity activity);

	void inject(WriteBlogPostActivity activity);

	void inject(BlogFragment fragment);

	void inject(BlogPostFragment fragment);

	void inject(FeedPostFragment fragment);

	void inject(ReblogFragment fragment);

	void inject(ReblogActivity activity);

	void inject(SettingsActivity activity);

	void inject(TransportsActivity activity);

	void inject(TestDataActivity activity);

	void inject(ChangePasswordActivity activity);

	void inject(IntroductionActivity activity);

	void inject(RssFeedImportActivity activity);

	void inject(RssFeedManageActivity activity);

	void inject(StartupFailureActivity activity);

	void inject(UnlockActivity activity);

	void inject(AddContactActivity activity);

	void inject(PendingContactListActivity activity);

	void inject(CrashReportActivity crashReportActivity);

	// Fragments

	void inject(AuthorNameFragment fragment);

	void inject(SetPasswordFragment fragment);

	void inject(DozeFragment fragment);

	void inject(PasswordFragment imageFragment);

	void inject(OpenDatabaseFragment activity);

	void inject(ContactListFragment fragment);

	void inject(CreateGroupFragment fragment);

	void inject(GroupListFragment fragment);

	void inject(GroupInviteFragment fragment);

	void inject(RevealContactsFragment activity);

	void inject(ForumListFragment fragment);

	void inject(FeedFragment fragment);

	void inject(KeyAgreementFragment fragment);

	void inject(LinkExchangeFragment fragment);

	void inject(NicknameFragment fragment);

	void inject(ContactChooserFragment fragment);

	void inject(ShareForumFragment fragment);

	void inject(ShareBlogFragment fragment);

	void inject(IntroductionMessageFragment fragment);

	void inject(SettingsFragment fragment);

	void inject(ScreenFilterDialogFragment fragment);

	void inject(ContactExchangeErrorFragment fragment);

	void inject(AliasDialogFragment aliasDialogFragment);

	void inject(ImageFragment imageFragment);

	void inject(ReportFormFragment reportFormFragment);

	void inject(CrashFragment crashFragment);

	void inject(ConfirmAvatarDialogFragment fragment);

}
