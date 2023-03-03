package omscompanion;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javafx.beans.property.SimpleBooleanProperty;
import omscompanion.openjfx.NewItemController;
import omscompanion.openjfx.QRFrameController;

public class ClipboardUtil {
	private static Thread t = null;
	private static final AtomicBoolean CHECK_CLIPBOARD = new AtomicBoolean(false);
	private static final SimpleBooleanProperty automaticModeProperty = new SimpleBooleanProperty(false);
	private static final String PROP_AUTO_CLIPBOARD_CHECK = "auto_clipboard_check",
			PROP_CLIPBOARD_CHECK_INTERVAL = "clipboard_check_interval";

	public static void init() {
		automaticModeProperty.addListener((observable, oldValue, newValue) -> {
			Main.properties.setProperty(PROP_AUTO_CLIPBOARD_CHECK, Boolean.toString(newValue));

			CHECK_CLIPBOARD.set(newValue);

			if (newValue)
				startClipboardCheck();

		});

		automaticModeProperty.set(isAutoClipboardCheck());
	}

	public static String get() throws UnsupportedFlavorException, IOException {
		var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
				? (String) clipboard.getData(DataFlavor.stringFlavor)
				: null;
	}

	public static SimpleBooleanProperty getAutomaticmodeProperty() {
		return automaticModeProperty;
	}

	public static void set(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
	}

	private static long getCheckInterval() {
		return Long.parseLong(Main.properties.getProperty(PROP_CLIPBOARD_CHECK_INTERVAL, "1000"));
	}

	public static boolean isAutoClipboardCheck() {
		return Boolean.parseBoolean(Main.properties.getProperty(PROP_AUTO_CLIPBOARD_CHECK, "true"));
	}

	public static void set(File... fArr) {
		var files = Arrays.stream(fArr).collect(Collectors.toList());
		var ft = new FileTransferable(files);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, null);
	}

	public static synchronized boolean checkClipboard() {
		try {
			var s = get();

			if (s == null)
				return false;

			s = s.trim();

			var message = MessageComposer.decode(s);

			if (message == null) // not a valid OMS message
				return false;

			CHECK_CLIPBOARD.set(false);

			QRFrameController.showForMessage(message, () -> {
				set(""); // clear the clipboard
				CHECK_CLIPBOARD.set(automaticModeProperty.get());
			});

			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;

	}

	protected static synchronized void startClipboardCheck() {
		if (t != null)
			return;

		t = new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					Thread.sleep(getCheckInterval());
				} catch (InterruptedException e) {
					return;
				}

				if (!CHECK_CLIPBOARD.get())
					continue;

				checkClipboard();
			}

			t = null;
		});

		t.setDaemon(true);

		t.start();
	}

	public static void processClipboard() {
		try {
			if (ClipboardUtil.checkClipboard()) // found oms:// in the clipboard
				return;

			// try to encrypt clipboard content
			var s = get();
			if (s == null || s.trim().isEmpty())
				return;

			CHECK_CLIPBOARD.set(false);

			NewItemController.showForMessage(s.getBytes(), () -> {
				set(""); // clear the clipboard
				CHECK_CLIPBOARD.set(automaticModeProperty.get());
			});

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static class FileTransferable implements Transferable {

		private List<File> list;

		public FileTransferable(List<File> list) {
			this.list = list;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors() {
			return new DataFlavor[] { DataFlavor.javaFileListFlavor };
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor) {
			return DataFlavor.javaFileListFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
			return list;
		}
	}
}
