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

public class TestDataActivity extends BriarActivity {

	@Inject
	TestDataCreator testDataCreator;

	private TextView[] textViews = new TextView[5];
	private SeekBar[] seekBars = new SeekBar[5];

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_test_data);
		textViews[0] = findViewById(R.id.textViewContactsSb);
		textViews[1] = findViewById(R.id.textViewMessagesSb);
		textViews[2] = findViewById(R.id.TextViewBlogPostsSb);
		textViews[3] = findViewById(R.id.TextViewForumsSb);
		textViews[4] = findViewById(R.id.TextViewForumMessagesSb);
		seekBars[0] = findViewById(R.id.seekBarContacts);
		seekBars[1] = findViewById(R.id.seekBarMessages);
		seekBars[2] = findViewById(R.id.seekBarBlogPosts);
		seekBars[3] = findViewById(R.id.seekBarForums);
		seekBars[4] = findViewById(R.id.seekBarForumMessages);

		for (int i = 0; i < 5; i++) {
			final TextView textView = textViews[i];
			seekBars[i].setOnSeekBarChangeListener(
					new OnSeekBarChangeListener() {
						@Override
						public void onProgressChanged(SeekBar seekBar,
								int progress,
								boolean fromUser) {
							textView.setText("" + progress);
						}

						@Override
						public void onStartTrackingTouch(SeekBar seekBar) {
						}

						@Override
						public void onStopTrackingTouch(SeekBar seekBar) {
						}

					});
		}

		findViewById(R.id.buttonCreateTestData).setOnClickListener(
				v -> {
					createTestData();
				});
	}

	private void createTestData() {
		testDataCreator.createTestData(seekBars[0].getProgress(),
				seekBars[1].getProgress(), seekBars[2].getProgress(),
				seekBars[3].getProgress(), seekBars[4].getProgress());
		Intent intent = new Intent(this, NavDrawerActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
