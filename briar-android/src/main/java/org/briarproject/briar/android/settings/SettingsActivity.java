package org.briarproject.briar.android.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.view.AuthorView;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceFragmentCompat.OnPreferenceStartFragmentCallback;
import de.hdodenhof.circleimageview.CircleImageView;

import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_AVATAR_IMAGE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SettingsActivity extends BriarActivity
		implements OnPreferenceStartFragmentCallback {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	FeatureFlags featureFlags;

	private SettingsViewModel settingsViewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		settingsViewModel = new ViewModelProvider(this, viewModelFactory)
				.get(SettingsViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_settings);

		if (featureFlags.shouldEnableProfilePictures()) {
			TextView textViewUserName = findViewById(R.id.username);
			CircleImageView imageViewAvatar =
					findViewById(R.id.avatarImage);

			settingsViewModel.getOwnIdentityInfo().observe(this, us -> {
				textViewUserName.setText(us.getLocalAuthor().getName());
				AuthorView.setAvatar(imageViewAvatar,
						us.getLocalAuthor().getId(), us.getAuthorInfo());
			});

			settingsViewModel.getSetAvatarFailed()
					.observeEvent(this, failed -> {
						if (failed) {
							Toast.makeText(this,
									R.string.change_profile_picture_failed_message,
									LENGTH_LONG).show();
						}
					});

			View avatarGroup = findViewById(R.id.avatarGroup);
			avatarGroup.setOnClickListener(e -> selectAvatarImage());
		} else {
			View view = findViewById(R.id.avatarGroup);
			view.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}

	private void selectAvatarImage() {
		Intent intent = UiUtils.createSelectImageIntent(false);
		startActivityForResult(intent, REQUEST_AVATAR_IMAGE);
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_AVATAR_IMAGE && result == RESULT_OK) {
			onAvatarImageReceived(data);
		}
	}

	private void onAvatarImageReceived(@Nullable Intent resultData) {
		if (resultData == null) return;
		Uri uri = resultData.getData();
		if (uri == null) return;

		ConfirmAvatarDialogFragment dialog =
				ConfirmAvatarDialogFragment.newInstance(uri);
		dialog.show(getSupportFragmentManager(),
				ConfirmAvatarDialogFragment.TAG);
	}

	@Override
	public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller,
			Preference pref) {
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentFactory fragmentFactory = fragmentManager.getFragmentFactory();
		Fragment fragment = fragmentFactory
				.instantiate(getClassLoader(), pref.getFragment());
		fragment.setTargetFragment(caller, 0);
		// Replace the existing Fragment with the new Fragment
		fragmentManager.beginTransaction()
				.setCustomAnimations(R.anim.step_next_in,
						R.anim.step_previous_out, R.anim.step_previous_in,
						R.anim.step_next_out)
				.replace(R.id.fragmentContainer, fragment)
				.addToBackStack(null)
				.commit();
		return true;
	}

}
