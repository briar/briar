package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import net.sf.briar.R;
import net.sf.briar.android.widgets.CommonLayoutParams;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CodesDoNotMatchView extends AddContactView
implements OnClickListener {

	CodesDoNotMatchView(Context ctx) {
		super(ctx);
	}

	void populate() {
		removeAllViews();
		Context ctx = getContext();
		LinearLayout innerLayout = new LinearLayout(ctx);
		innerLayout.setOrientation(HORIZONTAL);
		innerLayout.setGravity(CENTER);

		ImageView icon = new ImageView(ctx);
		icon.setPadding(10, 10, 10, 10);
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setTextSize(20);
		failed.setText(R.string.codes_do_not_match);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView interfering = new TextView(ctx);
		interfering.setGravity(CENTER_HORIZONTAL);
		interfering.setPadding(0, 0, 0, 10);
		interfering.setText(R.string.interfering);
		addView(interfering);

		Button tryAgain = new Button(ctx);
		tryAgain.setLayoutParams(CommonLayoutParams.WRAP_WRAP);
		tryAgain.setText(R.string.try_again_button);
		tryAgain.setOnClickListener(this);
		addView(tryAgain);
	}

	public void onClick(View view) {
		// Try again
		container.reset(new NetworkSetupView(container));
	}
}
