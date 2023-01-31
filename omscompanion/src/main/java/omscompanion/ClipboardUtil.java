package omscompanion;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ClipboardUtil {
	public static final int CHECK_INTERVAL = 1000;
	protected static boolean automaticMode = false;
	private static Thread t = null;
	private static final AtomicBoolean AUTO_CHECK_CLIPBOARD = new AtomicBoolean(true);

	public static String get() throws UnsupportedFlavorException, IOException {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
				? (String) clipboard.getData(DataFlavor.stringFlavor)
				: null;
	}

	public static void set(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
	}

	public static void set(File... fArr) {
		List<File> files = Arrays.stream(fArr).collect(Collectors.toList());
		FileTransferable ft = new FileTransferable(files);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, null);
	}

	public static synchronized boolean checkClipboard() {
		try {
			String s = get();

			if (s == null)
				return false;

			s = s.trim();

			if (!s.startsWith(MessageComposer.OMS_URL))
				return false;

			set("");

			s = s.substring(MessageComposer.OMS_URL.length());
			s = new String(Base64.getDecoder().decode(s));

			AUTO_CHECK_CLIPBOARD.set(false);
			new QRFrame(s, QRFrame.DELAY, () -> AUTO_CHECK_CLIPBOARD.set(automaticMode)).setVisible(true);

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
					Thread.sleep(CHECK_INTERVAL);
				} catch (InterruptedException e) {
					return;
				}

				if (!AUTO_CHECK_CLIPBOARD.get() || !automaticMode)
					continue;

				checkClipboard();
			}

			t = null;
		});

		t.start();
	}

	public static void setAutomaticMode(boolean b) {
		automaticMode = b;

		if (automaticMode)
			startClipboardCheck();
	}

	public static void processClipboard() {
		try {
			if (ClipboardUtil.checkClipboard()) // found oms:// in the clipboard
				return;

			// try to encrypt clipboard content
			String s = get();
			if (s == null)
				return;

			AUTO_CHECK_CLIPBOARD.set(false);

			try {
				new NewItem(s, () -> AUTO_CHECK_CLIPBOARD.set(true)).setVisible(true);
				;
			} catch (Exception ex) {
				ex.printStackTrace();
				AUTO_CHECK_CLIPBOARD.set(true);
			}

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
