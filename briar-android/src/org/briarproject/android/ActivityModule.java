package org.briarproject.android;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.briarproject.android.contact.ContactListFragment;
import org.briarproject.android.controller.BriarController;
import org.briarproject.android.controller.BriarControllerImpl;
import org.briarproject.android.controller.ConfigController;
import org.briarproject.android.controller.ConfigControllerImpl;
import org.briarproject.android.controller.DbController;
import org.briarproject.android.controller.DbControllerImpl;
import org.briarproject.android.controller.NavDrawerController;
import org.briarproject.android.controller.NavDrawerControllerImpl;
import org.briarproject.android.controller.PasswordController;
import org.briarproject.android.controller.PasswordControllerImpl;
import org.briarproject.android.controller.SetupController;
import org.briarproject.android.controller.SetupControllerImpl;
import org.briarproject.android.controller.TransportStateListener;
import org.briarproject.android.forum.ContactSelectorFragment;
import org.briarproject.android.forum.ForumListFragment;
import org.briarproject.android.forum.ShareForumMessageFragment;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.introduction.ContactChooserFragment;
import org.briarproject.android.introduction.IntroductionMessageFragment;
import org.briarproject.android.keyagreement.ChooseIdentityFragment;
import org.briarproject.android.keyagreement.ShowQrCodeFragment;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.android.BriarService.BriarServiceConnection;

@Module
public class ActivityModule {

	private final BaseActivity activity;

	public ActivityModule(BaseActivity activity) {
		this.activity = activity;
	}

	@ActivityScope
	@Provides
	BaseActivity provideBaseActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	Activity provideActivity() {
		return activity;
	}

	@ActivityScope
	@Provides
	protected SetupController provideSetupController(
			SetupControllerImpl setupControllerImpl) {
		return setupControllerImpl;
	}

	@ActivityScope
	@Provides
	protected ConfigController provideConfigController(
			ConfigControllerImpl configControllerImpl) {
		return configControllerImpl;
	}

	@ActivityScope
	@Provides
	protected SharedPreferences provideSharedPreferences(Activity activity) {
		return activity.getSharedPreferences("db", Context.MODE_PRIVATE);
	}

	@ActivityScope
	@Provides
	protected PasswordController providePasswordController(
			PasswordControllerImpl passwordControllerImpl) {
		return passwordControllerImpl;
	}

	@ActivityScope
	@Provides
	protected BriarController provideBriarController(
			BriarControllerImpl briarControllerImpl) {
		activity.addLifecycleController(briarControllerImpl);
		return briarControllerImpl;
	}

	@ActivityScope
	@Provides
	protected DbController provideDBController(
			DbControllerImpl dbController) {
		return dbController;
	}

	@ActivityScope
	@Provides
	protected NavDrawerController provideNavDrawerController(
			NavDrawerControllerImpl navDrawerControllerImpl) {
		activity.addLifecycleController(navDrawerControllerImpl);
		if (activity instanceof TransportStateListener) {
			navDrawerControllerImpl.setTransportListener(
					(TransportStateListener) activity);
		}
		return navDrawerControllerImpl;
	}

	@ActivityScope
	@Provides
	protected BriarServiceConnection provideBriarServiceConnection() {
		return new BriarServiceConnection();
	}

	@Provides
	@Named("ForumListFragment")
	BaseFragment provideForumListFragment(ForumListFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ContactListFragment")
	BaseFragment provideContactListFragment(ContactListFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ChooseIdentityFragment")
	BaseFragment provideChooseIdentityFragment(
			ChooseIdentityFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ShowQrCodeFragment")
	BaseFragment provideShowQrCodeFragment(ShowQrCodeFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ContactChooserFragment")
	BaseFragment provideContactChooserFragment(
			ContactChooserFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ContactSelectorFragment")
	ContactSelectorFragment provideContactSelectorFragment(
			ContactSelectorFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("ShareForumMessageFragment")
	ShareForumMessageFragment provideShareForumMessageFragment(
			ShareForumMessageFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}

	@Provides
	@Named("IntroductionMessageFragment")
	IntroductionMessageFragment provideIntroductionMessageFragment(
			IntroductionMessageFragment fragment) {
		fragment.setArguments(new Bundle());
		return fragment;
	}
}
