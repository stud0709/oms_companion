package omscompanion;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Base64;

import javafx.beans.property.SimpleBooleanProperty;
import omscompanion.crypto.KeyRequest;
import omscompanion.openjfx.NewItem;
import omscompanion.openjfx.QRFrame;

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

		boolean autoCheckClipboard = Boolean
				.parseBoolean(Main.properties.getProperty(PROP_AUTO_CLIPBOARD_CHECK, "true"));
		automaticModeProperty.set(autoCheckClipboard);
	}

	public static String getString() {
		try {
			var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

			return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
					? (String) clipboard.getData(DataFlavor.stringFlavor)
					: null;
		} catch (UnsupportedFlavorException | IOException ex) {
			return null;
		}
	}

	public static List<File> getFile() {
		try {
			var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

			return clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)
					? (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor)
					: null;
		} catch (UnsupportedFlavorException | IOException ex) {
			return null;
		}
	}

	/**
	 * This property defines if automatic clipboard check is activated. This can be
	 * suspended by {@link ClipboardUtil#suspendClipboardCheck()}.
	 * 
	 * @return
	 */
	public static SimpleBooleanProperty getAutomaticModeProperty() {
		return automaticModeProperty;
	}

	/**
	 * Suspends clipboard check until {@link #resumeClipboardCheck()} has been
	 * called.
	 */
	public static void suspendClipboardCheck() {
		CHECK_CLIPBOARD.set(false);
	}

	/**
	 * Resumes clipboard check if generally enabled.
	 * 
	 * @see ClipboardUtil#automaticModeProperty
	 */
	public static void resumeClipboardCheck() {
		CHECK_CLIPBOARD.set(automaticModeProperty.get());
	}

	public static void set(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
	}

	private static long getCheckInterval() {
		return Long.parseLong(Main.properties.getProperty(PROP_CLIPBOARD_CHECK_INTERVAL, "1000"));
	}

	public static void set(File... fArr) {
		var files = Arrays.stream(fArr).collect(Collectors.toList());
		var ft = new FileTransferable(files);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, null);
	}

	public static synchronized boolean checkClipboard() {
		try {
			var s = getString();

			if (s == null)
				return false;

			s = s.trim();

			var message = MessageComposer.decode(s);

			if (message == null) // not a valid OMS message
				return false;

			suspendClipboardCheck();

			QRFrame.showForMessage(message, true, false, _s -> {
				set(""); // clear the clipboard
				resumeClipboardCheck();
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
			var s = getString();

			if (s != null & !s.trim().isEmpty()) {
				suspendClipboardCheck();

				NewItem.showForMessage(s.getBytes(), () -> {
					set(""); // clear the clipboard
					resumeClipboardCheck();
				});

				return;
			}

			var listOfFile = getFile();
			var listOfFileDecrypted = new ArrayList<File>();

			if (listOfFile != null && listOfFile.isEmpty()) {
				suspendClipboardCheck();

				onListOfFile(listOfFile, listOfFileDecrypted);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void onListOfFile(List<File> listOfFile, List<File> listOfFileDecrypted)
			throws NoSuchAlgorithmException, IOException {
		if (listOfFile.isEmpty()) {
			set(listOfFileDecrypted.toArray(new File[] {}));
			resumeClipboardCheck();
			return;
		}

		// process single file
		File f = listOfFile.remove(0);

		var keyRequest = new KeyRequest(f);

		QRFrame.showForMessage(keyRequest.getMessage(), false, true, s -> {
			var message = Base64.decode(s);

			// to be continued... create output file, decrypt, add the file to
			// listOfFileDecrypted

			// keyRequest.onReply(f, ..., message);
		});
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
