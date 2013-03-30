package net.sf.briar.android;

import java.util.Comparator;

import net.sf.briar.api.Author;

public class AuthorNameComparator implements Comparator<Author> {

	public static final AuthorNameComparator INSTANCE =
			new AuthorNameComparator();

	public int compare(Author a1, Author a2) {
		return String.CASE_INSENSITIVE_ORDER.compare(a1.getName(),
				a2.getName());
	}
}
