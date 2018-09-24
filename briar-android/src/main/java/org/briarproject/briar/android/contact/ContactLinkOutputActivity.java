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

import static android.widget.Toast.LENGTH_SHORT;
import static org.briarproject.bramble.util.StringUtils.getRandomString;

public class ContactLinkOutputActivity extends BriarActivity {

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_contact_link_ouput);

		ActionBar ab = getSupportActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
		}
		setTitle(R.string.add_contact_via_link_title);

		String link = "briar://" + getRandomString(16);

		TextView linkView = findViewById(R.id.linkView);
		linkView.setText(link);

		ClipboardManager clipboard = (ClipboardManager)
				getSystemService(CLIPBOARD_SERVICE);
		if (clipboard == null) throw new AssertionError();
		ClipData clip = ClipData.newPlainText("Briar link", link);

		Button button = findViewById(R.id.button);
		button.setOnClickListener(v -> {
			clipboard.setPrimaryClip(clip);
			Toast.makeText(this, "Link copied!", LENGTH_SHORT).show();
		});

		Button enterLinkButton = findViewById(R.id.enterLinkButton);
		enterLinkButton.setOnClickListener(v -> startActivity(
				new Intent(this, ContactLinkInputActivity.class)));
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
