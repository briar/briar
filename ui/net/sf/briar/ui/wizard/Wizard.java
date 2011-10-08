package net.sf.briar.ui.wizard;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.i18n.Stri18ng;

public class Wizard implements I18n.Listener {

	private final I18n i18n;
	private final Stri18ng title, back, next, cancel, finish;
	private final Map<String, WizardPanel> panels;
	private final JPanel cardPanel;
	private final CardLayout cardLayout;
	private final JButton backButton, nextButton, cancelButton;
	private final JFrame frame;
	private final Object finishedLock = new Object();
	private WizardPanel currentPanel = null;
	private volatile boolean finished = false;

	public Wizard(I18n i18n, Stri18ng title, int width, int height) {
		this.i18n = i18n;
		this.title = title;
		back = new Stri18ng("BACK", i18n);
		next = new Stri18ng("NEXT", i18n);
		cancel = new Stri18ng("CANCEL", i18n);
		finish = new Stri18ng("FINISH", i18n);
		panels = new HashMap<String, WizardPanel>();
		cardPanel = new JPanel();
		cardPanel.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
		cardLayout = new CardLayout();
		cardPanel.setLayout(cardLayout);

		backButton = new JButton(back.tr());
		backButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				backButtonPressed();
			}
		});
		nextButton = new JButton(next.tr());
		nextButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				nextButtonPressed();
			}
		});
		cancelButton = new JButton(cancel.tr());
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				closeButtonPressed();
			}
		});

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.TRAILING));
		buttonPanel.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
		buttonPanel.add(backButton);
		buttonPanel.add(Box.createHorizontalStrut(10));
		buttonPanel.add(nextButton);
		buttonPanel.add(Box.createHorizontalStrut(30));
		buttonPanel.add(cancelButton);

		frame = new JFrame(title.tr());
		frame.setPreferredSize(new Dimension(width, height));
		frame.setResizable(false);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				closeButtonPressed();
			}
		});
		frame.getContentPane().add(cardPanel, BorderLayout.CENTER);
		frame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
	}

	public void localeChanged(Font uiFont) {
		backButton.setText(back.tr());
		backButton.setFont(uiFont);
		nextButton.setText(next.tr());
		nextButton.setFont(uiFont);
		synchronized(finishedLock) {
			if(finished) cancelButton.setText(finish.tr());
			else cancelButton.setText(cancel.tr());
		}
		cancelButton.setFont(uiFont);
		frame.setTitle(title.tr());
		for(WizardPanel panel : panels.values()) panel.localeChanged(uiFont);
		frame.applyComponentOrientation(i18n.getComponentOrientation());
		SwingUtilities.updateComponentTreeUI(frame);
	}

	public void display() {
		assert currentPanel != null;
		i18n.addListener(this);
		frame.pack();
		frame.setLocationRelativeTo(null); // Centre of the screen
		frame.setVisible(true);
	}

	public void close() {
		i18n.removeListener(this);
		frame.setVisible(false);
		frame.dispose();
	}

	public void registerPanel(String id, WizardPanel panel) {
		assert currentPanel == null;
		WizardPanel old = panels.put(id, panel);
		assert old == null;
		cardPanel.add(id, panel);
	}

	public void showPanel(String id) {
		currentPanel = panels.get(id);
		assert currentPanel != null;
		cardLayout.show(cardPanel, id);
		currentPanel.display();
	}

	public void setBackButtonEnabled(boolean enabled) {
		backButton.setEnabled(enabled);
	}

	public void setNextButtonEnabled(boolean enabled) {
		nextButton.setEnabled(enabled);
	}

	public void setFinished(boolean finished) {
		synchronized(finishedLock) {
			this.finished = finished;
			if(finished) {
				nextButton.setEnabled(false);
				cancelButton.setText(finish.tr());
			} else cancelButton.setText(cancel.tr());
		}
	}

	public Dimension getPreferredSize() {
		return frame.getPreferredSize();
	}

	private void backButtonPressed() {
		assert SwingUtilities.isEventDispatchThread();
		assert currentPanel != null;
		currentPanel.backButtonPressed();
	}

	private void nextButtonPressed() {
		assert SwingUtilities.isEventDispatchThread();
		assert currentPanel != null;
		currentPanel.nextButtonPressed();
	}

	private void closeButtonPressed() {
		assert SwingUtilities.isEventDispatchThread();
		assert currentPanel != null;
		cancelButton.setEnabled(false);
		synchronized(finishedLock) {
			if(finished) currentPanel.finishButtonPressed();
			else currentPanel.cancelButtonPressed();
		}
	}
}