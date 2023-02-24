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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import omscompanion.qr.AnimatedQrHelper;
import omscompanion.qr.QRFrame;

public class ClipboardUtil {
	public static final int CHECK_INTERVAL = 1000;
	protected static boolean automaticMode = false;
	private static Thread t = null;
	private static final AtomicBoolean CHECK_CLIPBOARD = new AtomicBoolean(false);
	private static Consumer<Boolean> onAutomaticModeChanged = null;

	public static String get() throws UnsupportedFlavorException, IOException {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
				? (String) clipboard.getData(DataFlavor.stringFlavor)
				: null;
	}

	public static void set(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
	}

	public static boolean isAutomaticMode() {
		return automaticMode;
	}

	public static void setOnAutomaticModeChanged(Consumer<Boolean> onAutomaticModeChanged) {
		ClipboardUtil.onAutomaticModeChanged = onAutomaticModeChanged;
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

			String m = MessageComposer.decode(s);

			if (m == null) // not a valid OMS message
				return false;

			set("");

			CHECK_CLIPBOARD.set(false);
			new QRFrame(m, AnimatedQrHelper.getSequenceDelay(), () -> CHECK_CLIPBOARD.set(automaticMode))
					.setVisible(true);

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

				if (!CHECK_CLIPBOARD.get())
					continue;

				checkClipboard();
			}

			t = null;
		});

		t.setDaemon(true);

		t.start();
	}

	public static void setAutomaticMode(boolean b) {
		automaticMode = b;
		CHECK_CLIPBOARD.set(b);

		onAutomaticModeChanged.accept(b);

		if (automaticMode)
			startClipboardCheck();

	}

	public static void processClipboard() {
		try {
			if (ClipboardUtil.checkClipboard()) // found oms:// in the clipboard
				return;

			// try to encrypt clipboard content
			String s = get();
			if (s == null || s.trim().isEmpty())
				return;

			CHECK_CLIPBOARD.set(false);

			try {
				new NewItem(s, () -> CHECK_CLIPBOARD.set(automaticMode)).setVisible(true);
			} catch (Exception ex) {
				ex.printStackTrace();
				CHECK_CLIPBOARD.set(automaticMode);
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
