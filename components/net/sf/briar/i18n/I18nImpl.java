package net.sf.briar.i18n;

import java.awt.ComponentOrientation;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.Set;

import javax.swing.UIManager;

import com.google.inject.Inject;

import net.sf.briar.api.i18n.FontManager;
import net.sf.briar.api.i18n.I18n;
import net.sf.briar.util.FileUtils;

public class I18nImpl implements I18n {

	/**
	 * Property keys for strings used in the JRE's built-in dialogs. Values
	 * assigned to these keys in i18n properties files will override the
	 * built-in values.
	 */
	private static final String[] uiManagerKeys = {
		"FileChooser.acceptAllFileFilterText",
		"FileChooser.cancelButtonText",
		"FileChooser.cancelButtonToolTipText",
		"FileChooser.detailsViewButtonAccessibleName",
		"FileChooser.detailsViewButtonToolTipText",
		"FileChooser.directoryOpenButtonText",
		"FileChooser.directoryOpenButtonToolTipText",
		"FileChooser.fileAttrHeaderText",
		"FileChooser.fileDateHeaderText",
		"FileChooser.fileNameHeaderText",
		"FileChooser.fileNameLabelText",
		"FileChooser.fileSizeHeaderText",
		"FileChooser.filesOfTypeLabelText",
		"FileChooser.fileTypeHeaderText",
		"FileChooser.helpButtonText",
		"FileChooser.helpButtonToolTipText",
		"FileChooser.homeFolderAccessibleName",
		"FileChooser.homeFolderToolTipText",
		"FileChooser.listViewButtonAccessibleName",
		"FileChooser.listViewButtonToolTipText",
		"FileChooser.lookInLabelText",
		"FileChooser.newFolderErrorText",
		"FileChooser.newFolderToolTipText",
		"FileChooser.openButtonText",
		"FileChooser.openButtonToolTipText",
		"FileChooser.openDialogTitleText",
		"FileChooser.saveButtonText",
		"FileChooser.saveButtonToolTipText",
		"FileChooser.saveDialogTitleText",
		"FileChooser.saveInLabelText",
		"FileChooser.updateButtonText",
		"FileChooser.updateButtonToolTipText",
		"FileChooser.upFolderAccessibleName",
		"FileChooser.upFolderToolTipText",
		"OptionPane.cancelButtonText",
		"OptionPane.noButtonText",
		"OptionPane.yesButtonText",
		"ProgressMonitor.progressText"
	};

	private final Object bundleLock = new Object();
	private final ClassLoader loader = I18n.class.getClassLoader();
	private final Set<Listener> listeners = new HashSet<Listener>();
	private final FontManager fontManager;

	private volatile Locale locale = Locale.getDefault();
	private volatile ResourceBundle bundle = null;

	@Inject
	public I18nImpl(FontManager fontManager) {
		this.fontManager = fontManager;
	}

	public String tr(String name) {
		loadResourceBundle();
		return bundle.getString(name);
	}

	private void loadResourceBundle() {
		if(bundle == null) {
			synchronized(bundleLock) {
				if(bundle == null) {
					bundle = ResourceBundle.getBundle("i18n", locale, loader);
					assert bundle != null;
					for(String key : uiManagerKeys) {
						try {
							UIManager.put(key, bundle.getString(key));
						} catch(MissingResourceException ignored) {}
					}
				}
			}
		}
	}

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		fontManager.setUiFontForLanguage(locale.getLanguage());
		Font uiFont = fontManager.getUiFont();
		synchronized(bundleLock) {
			this.locale = locale;
			Locale.setDefault(locale);
			bundle = null;
			synchronized(listeners) {
				for(Listener l : listeners) l.localeChanged(uiFont);
			}
		}
	}

	public void loadLocale() throws IOException {
		File root = FileUtils.getBriarDirectory();
		Scanner s = new Scanner(new File(root, "Data/locale.cfg"));
		if(s.hasNextLine()) setLocale(new Locale(s.nextLine()));
		s.close();
	}

	public void saveLocale() throws IOException {
		saveLocale(FileUtils.getBriarDirectory());
	}

	public void saveLocale(File dir) throws IOException {
		File localeCfg = new File(dir, "locale.cfg");
		FileOutputStream out = new FileOutputStream(localeCfg);
		PrintStream print = new PrintStream(out);
		print.println(locale);
		print.flush();
		print.close();
	}

	public ComponentOrientation getComponentOrientation() {
		return ComponentOrientation.getOrientation(locale);
	}

	public void addListener(Listener l) {
		l.localeChanged(fontManager.getUiFont());
		synchronized(listeners) {
			listeners.add(l);
		}
	}

	public void removeListener(Listener l) {
		synchronized(listeners) {
			listeners.remove(l);
		}
	}
}
