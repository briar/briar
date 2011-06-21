package net.sf.briar.api.i18n;

public class Stri18ng {

	private static final String HTML_OPEN_LEFT = "<html><body align='left'>";
	private static final String HTML_OPEN_RIGHT = "<html><body align='right'>";
	private static final String HTML_CLOSE = "</body></html>";
	private static final String PARAGRAPH = "<p><p>"; // Yes, two of them

	private final String name;
	private final I18n i18n;

	public Stri18ng(String name, I18n i18n) {
		this.name = name;
		this.i18n = i18n;
	}

	public String tr() {
		return i18n.tr(name);
	}

	public String html() {
		if(i18n.getComponentOrientation().isLeftToRight())
			return HTML_OPEN_LEFT + i18n.tr(name) + HTML_CLOSE;
		else return HTML_OPEN_RIGHT + i18n.tr(name) + HTML_CLOSE;
	}

	public String html(String... paras) {
		StringBuilder s = new StringBuilder();
		if(i18n.getComponentOrientation().isLeftToRight())
			s.append(HTML_OPEN_LEFT);
		else s.append(HTML_OPEN_RIGHT);
		s.append(tr());
		for(String para : paras) {
			s.append(PARAGRAPH);
			s.append(para);
		}
		s.append(HTML_CLOSE);
		return s.toString();
	}
}
