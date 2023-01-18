package omscompanion;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.Semaphore;

import javax.swing.JOptionPane;

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

			// TODO: multiple public keys
			Path pkPath = Files.list(Main.PUBLIC_KEY_STORAGE).filter(p -> !Files.isDirectory(p))
					.filter(p -> p.getFileName().toString().endsWith(".x.509")).findAny().get();

			RSAPublicKey pk = Main.getPublicKey(Files.readAllBytes(pkPath));

			String message = new EncryptedMessageTransfer(s.getBytes(), pk, EncryptedMessageTransfer.RSA_TRANSFORMATION)
					.getMessage();

			String omsURL = MessageComposer.asURL(message);

			SEMAPHORE.acquire();

			set(omsURL);

			JOptionPane.showMessageDialog(null,
					"Clipboard text converted to oms://... format.\nPress OK to clear the clipboard and continue.");

			set("");

			SEMAPHORE.release();

		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}
}
