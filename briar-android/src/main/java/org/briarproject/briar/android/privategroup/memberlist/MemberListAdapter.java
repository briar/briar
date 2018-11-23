package org.briarproject.briar.android.privategroup.memberlist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.util.BriarAdapter;

import static org.briarproject.briar.android.util.UiUtils.getContactDisplayName;

@NotNullByDefault
class MemberListAdapter extends
		BriarAdapter<MemberListItem, MemberListItemHolder> {

	MemberListAdapter(Context context) {
		super(context, MemberListItem.class);
	}

	@Override
	public MemberListItemHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
		View v = LayoutInflater.from(ctx).inflate(
				R.layout.list_item_group_member, viewGroup, false);
		return new MemberListItemHolder(v);
	}

	@Override
	public void onBindViewHolder(MemberListItemHolder ui, int position) {
		ui.bind(items.get(position));
	}

	@Override
	public int compare(MemberListItem m1, MemberListItem m2) {
		String n1 = getContactDisplayName(m1.getMember(),
				m1.getAuthorInfo().getAlias());
		String n2 = getContactDisplayName(m2.getMember(),
				m2.getAuthorInfo().getAlias());
		return n1.compareTo(n2);
	}

	@Override
	public boolean areContentsTheSame(MemberListItem m1, MemberListItem m2) {
		return m1.isOnline() == m2.isOnline() &&
				m1.getContactId() == m2.getContactId() &&
				m1.getStatus() == m2.getStatus();
	}

	@Override
	public boolean areItemsTheSame(MemberListItem m1, MemberListItem m2) {
		return m1.getMember().equals(m2.getMember());
	}

}
