package it.unibz.inf.ontop.protege.utils;

/*
 * #%L
 * ontop-protege
 * %%
 * Copyright (C) 2009 - 2013 KRDB Research Centre. Free University of Bozen Bolzano.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.injection.OntopStandaloneSQLSettings;
import it.unibz.inf.ontop.protege.mapping.DuplicateTriplesMapException;
import it.unibz.inf.ontop.protege.connection.DataSource;
import it.unibz.inf.ontop.protege.core.OntopProtegeReasoner;
import org.protege.editor.core.ProtegeManager;
import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.protege.editor.core.editorkit.EditorKit;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.awt.event.KeyEvent.*;

public class DialogUtils {


	/**
	 * Returns an ImageIcon, or null if the path was invalid.
	 */
	public static ImageIcon getImageIcon(String path) {
		java.net.URL imgURL = DialogUtils.class.getResource(path);
		if (imgURL != null) {
			return new ImageIcon(imgURL);
		} else {
			return null;
		}
	}

	public static ImageIcon getOntopIcon() {
		return getImageIcon("images/ontop-logo.png");
	}

	public static final String CANCEL_BUTTON_TEXT = UIManager.getString("OptionPane.cancelButtonText");
	public static final String OK_BUTTON_TEXT = UIManager.getString("OptionPane.okButtonText");

	public static void setLocationRelativeToProtegeAndOpen(EditorKit editorKit, JDialog dialog) {
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.pack();
		JFrame protegeFrame = ProtegeManager.getInstance().getFrame(editorKit.getWorkspace());
		dialog.setLocationRelativeTo(protegeFrame);
		dialog.setVisible(true);
	}

	public static boolean confirmation(Component parent, String message, String title) {
		return JOptionPane.showConfirmDialog(
				parent,
				message,
				title,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				getOntopIcon()) == JOptionPane.YES_OPTION;
	}

	public static OntopAbstractAction getStandardCloseWindowAction(String text, Window source) {
		return new OntopAbstractAction(text, null, null,
				KeyStroke.getKeyStroke(VK_ESCAPE, 0)) {
			@Override
			public void actionPerformed(ActionEvent e) {
				source.dispatchEvent(new WindowEvent(source, WindowEvent.WINDOW_CLOSING));
			}
		};
	}

	public static JButton getButton(OntopAbstractAction action) {
		JButton button = new JButton(action);
		button.setIconTextGap(5);
		button.setMargin(new Insets(3, 7, 3, 7));
		if (button.getToolTipText() != null)
			if (action.getAccelerator() == null)
				button.setToolTipText(action.getTooltip());
    		else
				button.setToolTipText(action.getTooltip() + " (" + keyStroke2String(action.getAccelerator()) + ")");
		return button;
	}

	public static JMenuItem getMenuItem(Action action) {
		return new JMenuItem(action);
	}

	public static JMenuItem getMenuItem(String text, Action action) {
		JMenuItem menuItem = new JMenuItem(action);
		menuItem.setText(text);
		menuItem.setIcon(null);
		return menuItem;
	}

	public static KeyStroke getKeyStrokeWithCtrlMask(int keyCode) {
		return KeyStroke.getKeyStroke(keyCode, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask());
	}

	public static void setUpPopUpMenu(JComponent component, JPopupMenu popupMenu) {
		int c = popupMenu.getComponentCount();
		for (int i = 0; i < c; i++) {
			Component current = popupMenu.getComponent(i);
			if (current instanceof JMenuItem) {
				JMenuItem menuItem = (JMenuItem) current;
				setUpAccelerator(component, menuItem.getAction());
			}
		}
		component.setComponentPopupMenu(popupMenu);
	}

	public static void setUpAccelerator(JComponent component, Action action) {
		KeyStroke keyStroke =  (KeyStroke) action.getValue(Action.ACCELERATOR_KEY);
		if (keyStroke != null)
			setUpAccelerator(component, action, keyStroke);
	}

	public static void setUpAccelerator(JComponent component, Action action, KeyStroke keyStroke) {
		component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, action.getValue(Action.NAME));
		component.getActionMap().put(action.getValue(Action.NAME), action);
	}

	private static String keyStroke2String(KeyStroke keyStroke) {
		return getKeyModifiersText(keyStroke.getModifiers()) + getKeyText(keyStroke.getKeyCode());
	}

	public static JButton createStandardButton(String text, boolean enabled) {
		JButton button = new JButton(text);
		button.addActionListener(e ->
				getOptionPaneParent((JComponent)e.getSource()).setValue(button));
		button.setEnabled(enabled);
		return button;
	}

	private static JOptionPane getOptionPaneParent(Container parent) {
		return  (parent instanceof JOptionPane)
				? (JOptionPane) parent
				: getOptionPaneParent(parent.getParent());
	}



	public static DefaultTableModel createNonEditableTableModel(Object[] columnNames) {
		return new DefaultTableModel(columnNames, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
	}

	public static final String HTML_TAB = "&nbsp;&nbsp;&nbsp;&nbsp;";

	public static String htmlEscape(String s) {
		return s.replaceAll("<", "&lt;")
				.replaceAll(">", "&gt;")
				.replaceAll("\t", HTML_TAB)
				.replaceAll("\n", "<br>");
	}

	public static String renderElapsedTime(long millis) {
		if (millis < 1_000)
			return String.format("%dms", millis);

		if (millis < 10_000)
			return String.format("%d.%02ds", millis / 1000, (millis % 1000 + 5)/10);

		if (millis < 100_000)
			return String.format("%d.%01ds", millis / 1000, (millis % 1000 + 50)/100);

		return String.format("%ds", (millis + 500)/ 1000);
	}

	public static Function<String, String> getExtensionReplacer(String replacement) {
		return shortForm -> {
			int i = shortForm.lastIndexOf(".");
			String filename = (i < 1) ?
					shortForm :
					shortForm.substring(0, i);
			return filename + replacement;
		};
	}

	public static JFileChooser getFileChooser(EditorKit editorKit, Function<String, String> filenameTransformer) {
		OWLEditorKit owlEditorKit = (OWLEditorKit) editorKit;
		OWLModelManager modelManager = owlEditorKit.getOWLWorkspace().getOWLModelManager();
		OWLOntology activeOntology = modelManager.getActiveOntology();
		IRI documentIRI = modelManager.getOWLOntologyManager().getOntologyDocumentIRI(activeOntology);
		File ontologyDir = new File(documentIRI.toURI().getPath());
		JFileChooser fc = new JFileChooser(ontologyDir);
		if (filenameTransformer != null)
			fc.setSelectedFile(new File(filenameTransformer.apply(documentIRI.getShortForm())));
		return fc;
	}

	public static boolean confirmCanWrite(File file, Container parent, String title) {
		return !file.exists() || JOptionPane.showConfirmDialog(parent,
				"<html><br>The file " + file.getPath() + " exists.<br><br>"
						+ "Do you want to <b>overwrite</b> it?<br></html>",
				title,
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				getOntopIcon()) == JOptionPane.YES_OPTION;
	}

	private static final int MAX_CHARACTERS_PER_LINE_COUNT = 150;

	public static void showPrettyMessageDialog(Component parent, Object message, String title, int type) {
		JOptionPane narrowPane = new JOptionPane(message, type) {
			@Override
			public int getMaxCharactersPerLineCount() {
				return MAX_CHARACTERS_PER_LINE_COUNT;
			}
		};
		JDialog errorDialog = narrowPane.createDialog(parent, title);
		errorDialog.setVisible(true);
	}


	public static Optional<OntopProtegeReasoner> getOntopProtegeReasoner(EditorKit editorKit) {
		if (!(editorKit instanceof OWLEditorKit))
			throw new MinorOntopInternalBugException("EditorKit is not an OWLEditorKit");

		OWLEditorKit owlEditorKit = (OWLEditorKit)editorKit;
		OWLReasoner reasoner = owlEditorKit.getModelManager().getOWLReasonerManager().getCurrentReasoner();
		if (!(reasoner instanceof OntopProtegeReasoner)) {
			JOptionPane.showMessageDialog(editorKit.getWorkspace(),
					"<html><b>Ontop reasoner</b> must be started before using this feature. To proceed<br><br>" +
							HTML_TAB + " * select Ontop in the <b>\"Reasoner\"</b> menu and<br>" +
							HTML_TAB + " * click <b>\"Start reasoner\"</b> in the same menu.<br></html>",
					"Warning",
					JOptionPane.WARNING_MESSAGE);
			return Optional.empty();
		}
		return Optional.of((OntopProtegeReasoner)reasoner);
	}

	public static void showCancelledActionDialog(Component parent, String title) {
		JOptionPane.showMessageDialog(parent,
				"<html><b>Process cancelled.</b> No changes made.<br></html>",
				title,
				JOptionPane.WARNING_MESSAGE);
	}

	public static void showErrorDialog(Component parent, String title, String message, Logger log, ExecutionException e, DataSource datasource) {
		Throwable cause = e.getCause();
		if (cause instanceof SQLException && datasource != null) {
			JOptionPane.showMessageDialog(parent,
					"<html><b>Error connecting to the database:</b> " + cause.getMessage() + ".<br><br>" +
							HTML_TAB + "JDBC driver: " + datasource.getDriver() + "<br>" +
							HTML_TAB + "Connection URL: " + datasource.getURL() + "<br>" +
							HTML_TAB + "Username: " + datasource.getUsername() + "</html>",
					title,
					JOptionPane.ERROR_MESSAGE);
		}
		else if (cause instanceof DuplicateTriplesMapException) {
			DuplicateTriplesMapException dm = (DuplicateTriplesMapException)cause;
			JOptionPane.showMessageDialog(parent,
					"<html><b>Duplicate mapping ID found.</b><br><br>" +
							HTML_TAB + "Please correct the Resource node name: <b>" + dm.getMessage() + "</b>.<br></html>",
					title,
					JOptionPane.ERROR_MESSAGE);
		}
		else if (cause instanceof OWLException) {
			OWLException owlException = (OWLException) cause;
			Throwable owlExceptionCause = owlException.getCause();
			JOptionPane.showMessageDialog(parent,
					"<html><b>Error executing SPARQL query.</b><br><br>" +
							HTML_TAB + owlExceptionCause.getMessage() + "</b>.<br></html>",
					title,
					JOptionPane.ERROR_MESSAGE);
		}
		else {
			DialogUtils.showSeeLogErrorDialog(parent, title, message, log, cause);
		}
	}


	public static void showSeeLogErrorDialog(Component parent, String title, String message, Logger log, Throwable e) {
		String text = message + "\n" +
				e.getMessage() + "\n" +
				"For more information, see the log.";

		JOptionPane narrowPane = new JOptionPane(text, JOptionPane.ERROR_MESSAGE) {
			@Override
			public int getMaxCharactersPerLineCount() {
				return MAX_CHARACTERS_PER_LINE_COUNT;
			}
		};
		JDialog errorDialog = narrowPane.createDialog(parent, title);
		errorDialog.setModal(true);
		errorDialog.setVisible(true);

		log.error(e.getMessage(), e);
		e.printStackTrace();
	}

	public static void showSeeLogErrorDialog(Component parent, String message, Logger log, Throwable e) {
		showSeeLogErrorDialog(parent, "Error", message, log, e);
	}

	public static void showQuickErrorDialog(Component parent, Exception e, String message) {
		SwingUtilities.invokeLater(() -> {
			JTextArea textArea = new JTextArea();
			textArea.setBackground(Color.WHITE);
			textArea.setFont(new Font("Monaco", Font.PLAIN, 11));
			textArea.setEditable(false);
			textArea.setWrapStyleWord(true);

			String debugInfo = e.getLocalizedMessage() + "\n\n"
					+ "###################################################\n"
					+ "##    Debugging information for developers    ##\n"
					+ "###################################################\n\n"
					+ Stream.of(e.getStackTrace())
						.map(StackTraceElement::toString)
						.collect(Collectors.joining("\n\t", "\t", ""));

			textArea.setText(debugInfo);
			textArea.setCaretPosition(0);

			JScrollPane scrollPane = new JScrollPane(textArea);
			scrollPane.setPreferredSize(new Dimension(800, 450));

			JOptionPane.showMessageDialog(parent, scrollPane, message, JOptionPane.ERROR_MESSAGE);
		});
	}

	public static void showInfoDialog(Component parent, String message, String title) {
		JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE, getOntopIcon());
	}
}
