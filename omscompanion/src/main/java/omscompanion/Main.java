package omscompanion;

import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class Main {
	public static Path PUBLIC_KEY_STORAGE = new File("public").toPath();
	public static final Properties properties = new Properties();
	public static final String PROP_DEFAULT_KEY = "default_key", PROP_AUTO_CLIPBOARD_CHECK = "auto_clipboard_check";

	public static void main(String[] args) throws Exception {
		Files.createDirectories(PUBLIC_KEY_STORAGE);
		File pf = new File("omscompanion.properties");
		if (pf.exists()) {
			try (FileReader fr = new FileReader(pf)) {
				properties.load(fr);
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try (FileWriter fw = new FileWriter(pf)) {
				properties.store(fw, null);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}));

		initTrayIcon();

		ClipboardUtil
				.setAutomaticMode(properties.getProperty(PROP_AUTO_CLIPBOARD_CHECK, "true").equalsIgnoreCase("true"));
	}

	private static void initTrayIcon() throws IOException, AWTException {
//		https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html

		if (!SystemTray.isSupported())
			return;

		Dimension size = SystemTray.getSystemTray().getTrayIconSize();

		BufferedImage bi = ImageIO.read(Main.class.getResourceAsStream("qr-code.png"));
		Image image = bi.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);
		TrayIcon trayIcon = new TrayIcon(image, "omsCompanion");

		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				// encrypt clipboard or send oms:// message per double click
				if (e.getClickCount() > 1) {
					new Thread(() -> ClipboardUtil.processClipboard()).start();
				}
			}
		});

		PopupMenu menu = new PopupMenu();
		{
			MenuItem processClipboard = new MenuItem("Process Clipboard");
			processClipboard.setFont(new JLabel().getFont().deriveFont(Font.BOLD));
			processClipboard.setActionCommand("processClipboard");
			processClipboard.addActionListener(MENU_ACTION_LISTENER);
			menu.add(processClipboard);
		}

		{
			String menuItemText = "Monitor clipboard";
			MenuItem monitorClipboard = new MenuItem(menuItemText);
			monitorClipboard.setActionCommand("autoClipboardCheck");
			monitorClipboard.addActionListener(MENU_ACTION_LISTENER);
			ClipboardUtil.setOnAutomaticModeChanged(b -> {
				properties.setProperty(PROP_AUTO_CLIPBOARD_CHECK, Boolean.toString(b));
				SwingUtilities.invokeLater(() -> {
					monitorClipboard.setLabel(menuItemText + (b ? " \u2713" : ""));
				});
			});
			menu.add(monitorClipboard);
		}

		{
			Menu crypto = new Menu("Cryptography...");

			{
				MenuItem menuItem = new MenuItem("New Private Key");
				menuItem.setActionCommand("newKeyPair");
				menuItem.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(menuItem);
			}

			{
				MenuItem menuItem = new MenuItem("Import Public Key");
				menuItem.setActionCommand("importPublicKey");
				menuItem.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(menuItem);
			}

			{
				MenuItem menuItem = new MenuItem("Public Key Folder");
				menuItem.setActionCommand("publicKeyFolder");
				menuItem.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(menuItem);
			}

			menu.add(crypto);
		}
		{
			MenuItem exitCommand = new MenuItem("Exit");
			exitCommand.setActionCommand("exit");
			exitCommand.addActionListener(MENU_ACTION_LISTENER);
			menu.add(exitCommand);
		}

		trayIcon.setPopupMenu(menu);

		SystemTray.getSystemTray().add(trayIcon);
	}

	private static final ActionListener MENU_ACTION_LISTENER = new ActionListener() {

		@Override
		public void actionPerformed(ActionEvent e) {
			switch (e.getActionCommand()) {
			case "exit":
				System.exit(0);
				break;
			case "processClipboard":
				ClipboardUtil.processClipboard();
				break;
			case "newKeyPair":
				new NewPrivateKey().getFrame().setVisible(true);
				break;
			case "autoClipboardCheck":
				ClipboardUtil.setAutomaticMode(!ClipboardUtil.isAutomaticMode());
				break;
			case "importPublicKey":
				new PublicKeyImport().setVisible(true);
				break;
			case "publicKeyFolder":
				try {
					Desktop.getDesktop().open(PUBLIC_KEY_STORAGE.toFile());
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				break;
			}
		}
	};

	/**
	 * Wrapper for -genkey command of java keytool.
	 * 
	 * @param keyalg       e.g. {@code RSA }
	 * @param alias        key alias
	 * @param keystorePath keystore path
	 * @param storepass    keystore master password
	 * @param validity     certificate validity in days
	 * @param keysize      e.g. 2048
	 * @param dname        e.g. {@code CN=localhost }
	 * @return {@link Process#exitValue() }
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Deprecated
	public static int genKey(String keyalg, String alias, Path keystorePath, String storepass, int validity,
			int keysize, String dname) throws IOException, InterruptedException {

		String keytoolPath = new File(System.getProperty("java.home")).toPath().resolve("bin").resolve("keytool.exe")
				.toAbsolutePath().toString();

		String cmd = keytoolPath + " -genkey" + " -keyalg " + keyalg + " -alias " + alias + " -keystore "
				+ keystorePath.toAbsolutePath() + " -storepass " + storepass + " -validity " + validity + " -keysize "
				+ keysize + " -dname \"" + dname + "\"";

		// System.out.println(cmd);

		Process keytool = Runtime.getRuntime().exec(cmd);
		return keytool.waitFor();
	}

	public static byte[] getFingerprint(RSAPublicKey publicKey) throws NoSuchAlgorithmException {
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		sha256.update(publicKey.getModulus().toByteArray());
		return sha256.digest(publicKey.getPublicExponent().toByteArray());
	}

	public static String byteArrayToHex(byte[] a) {
		StringBuilder sb = new StringBuilder(a.length * 2);
		int i = 0;
		for (byte b : a)
			sb.append(String.format("%02x", b)).append(i++ % 2 == 1 ? " " : "");
		return sb.toString();
	}

	public static RSAPublicKey getPublicKey(byte[] encoded) throws InvalidKeySpecException, NoSuchAlgorithmException {
		PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));

		return (RSAPublicKey) publicKey;
	}
}
