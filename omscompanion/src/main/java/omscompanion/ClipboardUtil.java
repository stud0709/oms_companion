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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public class ClipboardUtil {
	public static final int CHECK_INTERVAL = 1000;
	protected static boolean automaticMode = false;
	private static Thread t = null;
	private static final Semaphore SEMAPHORE = new Semaphore(1);

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

			new QRFrame(s, QRFrame.DELAY, () -> {
				if (automaticMode)
					startClipboardCheck();
			}).setVisible(true);

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
			while (!Thread.interrupted() && automaticMode) {
				try {
					Thread.sleep(CHECK_INTERVAL);
					SEMAPHORE.acquire();
				} catch (InterruptedException e) {
					return;
				} finally {
					SEMAPHORE.release();
				}

				if (checkClipboard())
					break;
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

			SEMAPHORE.acquire();

			try {
				new NewItem(s, () -> SEMAPHORE.release()).setVisible(true);
			} catch (Exception ex) {
				ex.printStackTrace();
				SEMAPHORE.release();
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
