package net.sf.briar.android.helloworld;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;

import java.util.logging.Logger;

import net.sf.briar.R;
import net.sf.briar.android.BriarActivity;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.api.android.BundleEncrypter;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

import com.google.inject.Inject;

public class HelloWorldActivity extends BriarActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(HelloWorldActivity.class.getName());

	@Inject private BundleEncrypter bundleEncrypter;

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(null);
		if(LOG.isLoggable(INFO)) LOG.info("Created");
		LinearLayout layout = new LinearLayout(this);
		layout.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
		layout.setOrientation(VERTICAL);
		layout.setGravity(CENTER_HORIZONTAL);

		TextView welcome = new TextView(this);
		welcome.setPadding(0, 0, 0, 10);
		welcome.setText(R.string.welcome);
		layout.addView(welcome);

		TextView faceToFace = new TextView(this);
		faceToFace.setPadding(0, 0, 0, 10);
		faceToFace.setText(R.string.face_to_face);
		layout.addView(faceToFace);

		Button addContact = new Button(this);
		LayoutParams lp = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
		addContact.setLayoutParams(lp);
		addContact.setText(R.string.add_contact_button);
		addContact.setCompoundDrawablesWithIntrinsicBounds(
				R.drawable.social_add_person, 0, 0, 0);
		addContact.setOnClickListener(this);
		layout.addView(addContact);

		setContentView(layout);

		startService(new Intent(BriarService.class.getName()));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(LOG.isLoggable(INFO)) LOG.info("Destroyed");
	}

	public void onClick(View view) {
		startActivity(new Intent(this, AddContactActivity.class));
	}
}
