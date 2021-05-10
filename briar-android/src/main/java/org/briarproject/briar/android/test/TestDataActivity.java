package org.briarproject.briar.android.test;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.api.test.TestDataCreator;

import javax.inject.Inject;

import androidx.appcompat.app.ActionBar;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static org.briarproject.briar.android.BriarApplication.ENTRY_ACTIVITY;

public class TestDataActivity extends BriarActivity {

	@Inject
	TestDataCreator testDataCreator;

	private TextView contactsTextView, forumsTextView;
	private SeekBar contactsSeekBar, messagesSeekBar, avatarsSeekBar,
			blogPostsSeekBar, forumsSeekBar, forumPostsSeekBar;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_test_data);
		contactsTextView = findViewById(R.id.textViewContactsSb);
		TextView messagesTextView = findViewById(R.id.textViewMessagesSb);
		TextView avatarsTextView = findViewById(R.id.textViewAvatarsSb);
		TextView blogPostsTextView = findViewById(R.id.TextViewBlogPostsSb);
		forumsTextView = findViewById(R.id.TextViewForumsSb);
		TextView forumPostsTextView =
				findViewById(R.id.TextViewForumMessagesSb);
		contactsSeekBar = findViewById(R.id.seekBarContacts);
		messagesSeekBar = findViewById(R.id.seekBarMessages);
		avatarsSeekBar = findViewById(R.id.seekBarAvatars);
		blogPostsSeekBar = findViewById(R.id.seekBarBlogPosts);
		forumsSeekBar = findViewById(R.id.seekBarForums);
		forumPostsSeekBar = findViewById(R.id.seekBarForumMessages);

		contactsSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						contactsTextView.setText(String.valueOf(progress + 1));
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
				});

		messagesSeekBar.setOnSeekBarChangeListener(
				new OnSeekBarChangeUpdateProgress(messagesTextView));
		avatarsSeekBar.setOnSeekBarChangeListener(
				new OnSeekBarChangeUpdateProgress(avatarsTextView));
		blogPostsSeekBar.setOnSeekBarChangeListener(
				new OnSeekBarChangeUpdateProgress(blogPostsTextView));
		forumsSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						forumsTextView.setText(String.valueOf(progress));
						forumPostsSeekBar.setEnabled(progress > 0);
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
				});
		forumPostsSeekBar.setOnSeekBarChangeListener(
				new OnSeekBarChangeUpdateProgress(forumPostsTextView));

		findViewById(R.id.buttonZeroValues).setOnClickListener(
				v -> {
					contactsSeekBar.setProgress(0);
					messagesSeekBar.setProgress(0);
					avatarsSeekBar.setProgress(0);
					blogPostsSeekBar.setProgress(0);
					forumsSeekBar.setProgress(0);
					forumPostsSeekBar.setProgress(0);
				});

		findViewById(R.id.buttonCreateTestData).setOnClickListener(
				v -> createTestData());
	}

	private void createTestData() {
		testDataCreator.createTestData(contactsSeekBar.getProgress() + 1,
				messagesSeekBar.getProgress(), avatarsSeekBar.getProgress(),
				blogPostsSeekBar.getProgress(), forumsSeekBar.getProgress(),
				forumPostsSeekBar.getProgress());
		Intent intent = new Intent(this, ENTRY_ACTIVITY);
		intent.addFlags(FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();
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

	private static class OnSeekBarChangeUpdateProgress
			implements OnSeekBarChangeListener {
		private final TextView textView;

		private OnSeekBarChangeUpdateProgress(TextView textView) {
			this.textView = textView;
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			textView.setText(String.valueOf(progress));
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	}

}
