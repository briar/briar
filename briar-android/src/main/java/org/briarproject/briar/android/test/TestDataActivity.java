package org.briarproject.briar.android.test;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.navdrawer.NavDrawerActivity;
import org.briarproject.briar.api.test.TestDataCreator;

import javax.inject.Inject;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;

public class TestDataActivity extends BriarActivity {

	@Inject
	TestDataCreator testDataCreator;

	private TextView contactsTextView;
	private SeekBar contactsSeekBar;

	private TextView messagesTextView;
	private SeekBar messagesSeekBar;

	private TextView blogPostsTextView;
	private SeekBar blogPostsSeekBar;

	private TextView forumsTextView;
	private SeekBar forumsSeekBar;

	private TextView forumPostsTextView;
	private SeekBar forumPostsSeekBar;

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
		messagesTextView = findViewById(R.id.textViewMessagesSb);
		blogPostsTextView = findViewById(R.id.TextViewBlogPostsSb);
		forumsTextView = findViewById(R.id.TextViewForumsSb);
		forumPostsTextView = findViewById(R.id.TextViewForumMessagesSb);
		contactsSeekBar = findViewById(R.id.seekBarContacts);
		messagesSeekBar = findViewById(R.id.seekBarMessages);
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

		messagesSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						messagesTextView.setText(String.valueOf(progress));
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
				});

		blogPostsSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						blogPostsTextView.setText(String.valueOf(progress));
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
				});

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

		forumPostsSeekBar
				.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						forumPostsTextView.setText(String.valueOf(progress));
					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
					}
				});

		findViewById(R.id.buttonCreateTestData).setOnClickListener(
				v -> createTestData());
	}

	private void createTestData() {
		testDataCreator.createTestData(contactsSeekBar.getProgress() + 1,
				messagesSeekBar.getProgress(), blogPostsSeekBar.getProgress(),
				forumsSeekBar.getProgress(), forumPostsSeekBar.getProgress());
		Intent intent = new Intent(this, NavDrawerActivity.class);
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
}
