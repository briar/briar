package org.briarproject.android.contact;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.briarproject.R;
import org.briarproject.api.messaging.PrivateMessageHeader;
import org.briarproject.util.StringUtils;

import java.util.ArrayList;

import static org.briarproject.api.messaging.PrivateMessageHeader.Status.DELIVERED;
import static org.briarproject.api.messaging.PrivateMessageHeader.Status.SENT;

class ConversationAdapter extends ArrayAdapter<ConversationItem> {

	ConversationAdapter(Context ctx) {
		super(ctx, android.R.layout.simple_expandable_list_item_1,
				new ArrayList<ConversationItem>());
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ConversationItem item = getItem(position);
		PrivateMessageHeader header = item.getHeader();
		Context ctx = getContext();

		LayoutInflater inflater = (LayoutInflater) ctx.getSystemService
				(Context.LAYOUT_INFLATER_SERVICE);

		View v;
		if (header.isLocal()) {
			v = inflater.inflate(R.layout.list_item_msg_out, null);

			ImageView status = (ImageView) v.findViewById(R.id.msgStatus);
			if (item.getStatus() == DELIVERED) {
				status.setImageResource(R.drawable.message_delivered);
			} else if (item.getStatus() == SENT) {
				status.setImageResource(R.drawable.message_sent);
			} else {
				status.setImageResource(R.drawable.message_stored);
			}
		} else {
			v = inflater.inflate(R.layout.list_item_msg_in, null);
		}

		TextView body = (TextView) v.findViewById(R.id.msgBody);

		if (item.getBody() == null) {
			body.setText("\u2026");
		} else if (header.getContentType().equals("text/plain")) {
			body.setText(StringUtils.fromUtf8(item.getBody()));
		} else {
			// TODO support other content types
		}

		TextView date = (TextView) v.findViewById(R.id.msgTime);
		long timestamp = header.getTimestamp();
		date.setText(DateUtils.getRelativeTimeSpanString(ctx, timestamp));

		return v;
	}
}