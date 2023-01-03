package omscompanion;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Base64;

public class ClipboardUtil {
	public static final int CHECK_INTERVAL = 1000;

	public static String get() throws UnsupportedFlavorException, IOException {
		Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

		return clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)
				? (String) clipboard.getData(DataFlavor.stringFlavor)
				: null;
	}

	public static void set(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
	}

	public static boolean checkClipboard() {
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

			new QRFrame(s, QRFrame.DELAY, () -> startClipboardCheck()).setVisible(true);

			return true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;

	}

	public static void startClipboardCheck() {
		new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					Thread.sleep(CHECK_INTERVAL);
				} catch (InterruptedException e) {
					return;
				}

				if (checkClipboard())
					break;
			}
		}).start();
	}
}
