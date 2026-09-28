package com.gieligotchi.ui;

import com.gieligotchi.model.ProfileState;
import com.gieligotchi.service.GieligotchiStateService;
import com.gieligotchi.service.SaveCodec;
import java.awt.Component;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/** File operations run off the EDT; imports are explicit, character-checked replacements. */
final class SaveManagerDialog
{
	private SaveManagerDialog() { }

	static void show(Component parent, GieligotchiStateService service)
	{
		String[] actions = {"Export", "Import", "Resolve conflict", "Recovery files", "Close"};
		JTextArea message = new JTextArea(service.getSyncStatus()
			+ "\n\nExport includes eggs, companions, collection, memories and goals."
			+ "\nImport replaces this character's progress; recovery copies are kept."
			+ "\nUse one client at a time. A queued save is not a confirmed cloud upload.", 7, 45);
		message.setEditable(false);
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setOpaque(false);
		int action = JOptionPane.showOptionDialog(parent, message, "Gieligotchi · Save & sync",
			JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, actions, actions[4]);
		boolean recovery = action == 3;
		if (recovery) { action = 1; }
		ProfileState current = service.getState();
		if (action < 0 || action > 2) { return; }
		if (current == null) { error(parent, "Log into your OSRS character first."); return; }
		String character = current.getProfileKey();
		if (action == 2)
		{
			if (!service.hasSaveConflict()) { error(parent, "There is no save conflict to resolve."); return; }
			List<String> choices = service.getSaveChoices();
			String token = service.getConflictToken();
			Object selected = JOptionPane.showInputDialog(parent,
				"Choose the progress to keep. All alternatives are backed up first.\nProgress is not combined.",
				"Resolve save conflict", JOptionPane.WARNING_MESSAGE, null, choices.toArray(), choices.get(0));
			if (selected != null)
			{
				run(parent, () -> {
					service.resolveSave(choices.indexOf(selected), character, token);
					return "Choice saved. Recovery copies are available.";
				});
			}
			return;
		}
		JFileChooser chooser = new JFileChooser();
		if (recovery)
		{
			chooser.setCurrentDirectory(service.getSaveDirectory().toFile());
			chooser.setDialogTitle("Restore a recovery save");
		}
		chooser.setFileFilter(new FileNameExtensionFilter("Gieligotchi save (*.json)", "json"));
		if (action == 0)
		{
			chooser.setSelectedFile(new java.io.File("gieligotchi-" + character + "-" + System.currentTimeMillis() + ".json"));
			if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) { return; }
			Path target = chooser.getSelectedFile().toPath();
			String snapshot = service.exportSave();
			run(parent, () -> {
				Files.writeString(target, snapshot, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				return "Save exported. Copy this file to your other device.\nKeep it private; it contains your character's progress.";
			});
		}
		else
		{
			if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) { return; }
			Path source = chooser.getSelectedFile().toPath();
			if (JOptionPane.showConfirmDialog(parent, "Replace this character's progress with the selected save?\n"
				+ "This does not combine collections. A recovery copy will be kept.", "Import save",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) { return; }
			run(parent, () -> {
				if (Files.size(source) > SaveCodec.MAX_FILE_BYTES) { throw new IllegalArgumentException("Save file is too large."); }
				String text = Files.readString(source, StandardCharsets.UTF_8);
				service.importSave(text, character);
				return "Save imported. Your previous save is in Recovery files.";
			});
		}
	}

	private static void run(Component parent, java.util.concurrent.Callable<String> task)
	{
		new SwingWorker<String, Void>()
		{
			@Override protected String doInBackground() throws Exception { return task.call(); }
			@Override protected void done()
			{
				try { JOptionPane.showMessageDialog(parent, get(), "Gieligotchi saves", JOptionPane.INFORMATION_MESSAGE); }
				catch (Exception failure)
				{
					Throwable cause = failure.getCause() == null ? failure : failure.getCause();
					error(parent, cause instanceof java.nio.file.FileAlreadyExistsException
						? "That file already exists. Choose a new name to keep both backups." : cause.getMessage());
				}
			}
		}.execute();
	}

	private static void error(Component parent, String message)
	{
		JOptionPane.showMessageDialog(parent, message, "Gieligotchi saves", JOptionPane.WARNING_MESSAGE);
	}
}
