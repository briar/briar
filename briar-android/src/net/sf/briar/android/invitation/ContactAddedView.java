package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static net.sf.briar.android.widgets.CommonLayoutParams.WRAP_WRAP;
import net.sf.briar.R;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ContactAddedView extends AddContactView
implements OnClickListener {

	ContactAddedView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setImageResource(R.drawable.navigation_accept);
		innerLayout.addView(icon);

		TextView added = new TextView(ctx);
		added.setTextSize(22);
		added.setPadding(10, 10, 10, 10);
		added.setText(R.string.contact_added);
		innerLayout.addView(added);
		addView(innerLayout);

		TextView contactName = new TextView(ctx);
		contactName.setGravity(CENTER);
		contactName.setTextSize(22);
		contactName.setPadding(10, 0, 10, 10);
		contactName.setText(container.getContactName());
		addView(contactName);

		Button doneButton = new Button(ctx);
		doneButton.setLayoutParams(WRAP_WRAP);
		doneButton.setText(R.string.done_button);
		doneButton.setOnClickListener(this);
		addView(doneButton);
	}

	public void onClick(View view) {
		container.finish();
	}
}
