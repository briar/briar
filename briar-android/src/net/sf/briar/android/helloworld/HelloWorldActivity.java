package net.sf.briar.android.helloworld;

import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.VERTICAL;
import static java.util.logging.Level.INFO;

import java.util.logging.Logger;

import com.google.inject.Inject;
import com.google.inject.Provider;

import net.sf.briar.R;
import net.sf.briar.android.BriarService;
import net.sf.briar.android.invitation.AddContactActivity;
import net.sf.briar.api.android.BundleEncrypter;
import roboguice.activity.RoboActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;

public class HelloWorldActivity extends RoboActivity
implements OnClickListener {

	private static final Logger LOG =
			Logger.getLogger(HelloWorldActivity.class.getName());

	@Inject private static Provider<BundleEncrypter> bundleEncrypterProvider;

	private final BundleEncrypter bundleEncrypter =
			bundleEncrypterProvider.get();

	@Override
	public void onCreate(Bundle state) {
		if(state != null && !bundleEncrypter.decrypt(state)) state = null;
		super.onCreate(state);
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
	public void onRestoreInstanceState(Bundle state) {
		if(bundleEncrypter.decrypt(state))
			super.onRestoreInstanceState(state);
	}

	@Override
	public void onSaveInstanceState(Bundle state) {
		super.onSaveInstanceState(state);
		bundleEncrypter.encrypt(state);
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
