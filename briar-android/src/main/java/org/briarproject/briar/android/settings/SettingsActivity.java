package org.briarproject.briar.android.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.FeatureFlags;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.util.UiUtils;
import org.briarproject.briar.android.view.AuthorView;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import de.hdodenhof.circleimageview.CircleImageView;

import static android.widget.Toast.LENGTH_LONG;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_AVATAR_IMAGE;

public class SettingsActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	private SettingsViewModel settingsViewModel;

	@Inject
	FeatureFlags featureFlags;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_settings);

		if (featureFlags.shouldEnableProfilePictures()) {
			ViewModelProvider provider =
					new ViewModelProvider(this, viewModelFactory);
			settingsViewModel = provider.get(SettingsViewModel.class);

			TextView textViewUserName = findViewById(R.id.username);
			CircleImageView imageViewAvatar =
					findViewById(R.id.avatarImage);

			settingsViewModel.getOwnIdentityInfo().observe(this, us -> {
				textViewUserName.setText(us.getLocalAuthor().getName());
				AuthorView.setAvatar(imageViewAvatar,
						us.getLocalAuthor().getId(), us.getAuthorInfo());
			});

			settingsViewModel.getSetAvatarFailed().observe(this, failed -> {
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
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
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

}
