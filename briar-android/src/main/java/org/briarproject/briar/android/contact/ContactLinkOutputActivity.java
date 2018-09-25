package org.briarproject.briar.android.contact;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import javax.annotation.Nullable;

import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.EXTRA_TEXT;
import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.bramble.util.StringUtils.getRandomBase32String;

public class ContactLinkOutputActivity extends BriarActivity {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_contact_link_output);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}

		String link = "briar://" + getRandomBase32String(64);

		TextView linkView = findViewById(R.id.linkView);
		linkView.setText(link);

		ClipboardManager clipboard = (ClipboardManager)
				getSystemService(CLIPBOARD_SERVICE);
		if (clipboard == null) throw new AssertionError();
		ClipData clip = ClipData.newPlainText(
				getString(R.string.link_clip_label), link);

		Button copyButton = findViewById(R.id.copyButton);
		copyButton.setOnClickListener(v -> {
			clipboard.setPrimaryClip(clip);
			Toast.makeText(this, R.string.link_copied_toast, LENGTH_SHORT)
					.show();
		});

		Button shareButton = findViewById(R.id.shareButton);
		shareButton.setOnClickListener(v ->  {
			Intent i = new Intent(ACTION_SEND);
			i.putExtra(EXTRA_TEXT, link);
			i.setType("text/plain");
			startActivity(i);
		});
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

}
