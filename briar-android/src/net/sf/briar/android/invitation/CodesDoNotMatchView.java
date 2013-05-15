package net.sf.briar.android.invitation;

import static android.view.Gravity.CENTER;
import static net.sf.briar.android.util.CommonLayoutParams.WRAP_WRAP;
import net.sf.briar.R;
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
		icon.setImageResource(R.drawable.alerts_and_states_error);
		innerLayout.addView(icon);

		TextView failed = new TextView(ctx);
		failed.setTextSize(22);
		failed.setPadding(10, 10, 10, 10);
		failed.setText(R.string.codes_do_not_match);
		innerLayout.addView(failed);
		addView(innerLayout);

		TextView interfering = new TextView(ctx);
		interfering.setTextSize(14);
		interfering.setPadding(10, 0, 10, 10);
		interfering.setText(R.string.interfering);
		addView(interfering);

		Button tryAgainButton = new Button(ctx);
		tryAgainButton.setLayoutParams(WRAP_WRAP);
		tryAgainButton.setText(R.string.try_again_button);
		tryAgainButton.setOnClickListener(this);
		addView(tryAgainButton);
	}

	public void onClick(View view) {
		// Try again
		container.reset(new NetworkSetupView(container));
	}
}
