package omscompanion;

import java.awt.AWTException;
import java.awt.Desktop;
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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import omscompanion.openjfx.FileSync;
import omscompanion.openjfx.NewPrivateKey;
import omscompanion.openjfx.PasswordGenerator;
import omscompanion.openjfx.PublicKeyImport;

public class Main {
	public static Path PUBLIC_KEY_STORAGE = new File("public").toPath(), TMP = new File("tmp").toPath();
	public static final Properties properties = new Properties();
	private static final String PROP_DEFAULT_KEY = "default_key", PROPERTY_FILE = "omscompanion.properties";
	public static final String APP_NAME = "omsCompanion";
	private static MenuItem filesyncMenuItem = null, passwordGeneratorMenuItem = null, importPublicKeyMenuItem = null,
			newPrivateKeyMenuItem = null;

	public static void main(String[] args) throws Exception {
		Files.createDirectories(PUBLIC_KEY_STORAGE);
		Files.createDirectories(TMP);

		purge(TMP.toFile());

		var pf = new File(PROPERTY_FILE);
		if (pf.exists()) {
			try (var fr = new FileReader(pf)) {
				properties.load(fr);
			}
		}
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try (var fw = new FileWriter(pf)) {
				properties.store(fw, null);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}));

		initTrayIcon();

		ClipboardUtil.init();

		FxMain.main(args);
	}

	private static void purge(File dir) throws Exception {
		for (var f : dir.listFiles()) {
			try {
				if (f.isDirectory())
					purge(f);

				f.delete();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static String getDefaultKey() {
		return properties.getProperty(PROP_DEFAULT_KEY);
	}

	public static void setDefaultKeyAlias(String alias) {
		properties.setProperty(PROP_DEFAULT_KEY, alias);
	}

	private static void initTrayIcon() throws IOException, AWTException {
//		https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html

		if (!SystemTray.isSupported())
			return;

		var size = SystemTray.getSystemTray().getTrayIconSize();

		var bi = ImageIO.read(Main.class.getResourceAsStream("/omscompanion/img/qr-code.png"));
		var image = bi.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);
		var trayIcon = new TrayIcon(image, APP_NAME);

		trayIcon.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				// encrypt clipboard or send oms:// message per double click
				if (e.getClickCount() > 1) {
					new Thread(() -> ClipboardUtil.processClipboard()).start();
				}
			}
		});

		var menu = new PopupMenu();
		{
			var processClipboard = new MenuItem("Process Clipboard");
			processClipboard.setFont(new JLabel().getFont().deriveFont(Font.BOLD));
			processClipboard.setActionCommand("processClipboard");
			processClipboard.addActionListener(MENU_ACTION_LISTENER);
			menu.add(processClipboard);
		}

		{
			var menuItemText = "Monitor clipboard";
			var monitorClipboard = new MenuItem(menuItemText);
			monitorClipboard.setActionCommand("autoClipboardCheck");
			monitorClipboard.addActionListener(MENU_ACTION_LISTENER);
			ClipboardUtil.getAutomaticModeProperty().addListener((observable, oldValue, newValue) -> {
				SwingUtilities.invokeLater(() -> {
					monitorClipboard.setLabel(menuItemText + (newValue ? " \u2713" : ""));
				});
			});
			menu.add(monitorClipboard);
		}

		{
			passwordGeneratorMenuItem = new MenuItem("Password Generator");
			passwordGeneratorMenuItem.setActionCommand("pwdGen");
			passwordGeneratorMenuItem.addActionListener(MENU_ACTION_LISTENER);
			menu.add(passwordGeneratorMenuItem);
		}

		{
			filesyncMenuItem = new MenuItem("FileSync");
			filesyncMenuItem.setActionCommand("filesync");
			filesyncMenuItem.addActionListener(MENU_ACTION_LISTENER);
			menu.add(filesyncMenuItem);
		}

		{
			var crypto = new Menu("Cryptography...");

			{
				newPrivateKeyMenuItem = new MenuItem("New Private Key");
				newPrivateKeyMenuItem.setActionCommand("newPrivateKey");
				newPrivateKeyMenuItem.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(newPrivateKeyMenuItem);
			}

			{
				importPublicKeyMenuItem = new MenuItem("Import Public Key");
				importPublicKeyMenuItem.setActionCommand("importPublicKey");
				importPublicKeyMenuItem.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(importPublicKeyMenuItem);
			}

			{
				var menuItem = new MenuItem("Public Key Folder");
				menuItem.setActionCommand("publicKeyFolder");
				menuItem.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(menuItem);
			}

			menu.add(crypto);
		}

		{
			var menuItem = new MenuItem("Project Home Page");
			menuItem.setActionCommand("home");
			menuItem.addActionListener(MENU_ACTION_LISTENER);
			menu.add(menuItem);
		}

		{
			var exitCommand = new MenuItem("Exit");
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
			case "newPrivateKey":
				newPrivateKeyMenuItem.setEnabled(false);
				NewPrivateKey.show(() -> SwingUtilities.invokeLater(() -> newPrivateKeyMenuItem.setEnabled(true)));
				break;
			case "autoClipboardCheck":
				// revert value
				var b = ClipboardUtil.getAutomaticModeProperty().get();
				ClipboardUtil.getAutomaticModeProperty().set(!b);
				break;
			case "importPublicKey":
				importPublicKeyMenuItem.setEnabled(false);
				PublicKeyImport.show(() -> SwingUtilities.invokeLater(() -> importPublicKeyMenuItem.setEnabled(true)));
				break;
			case "pwdGen":
				passwordGeneratorMenuItem.setEnabled(false);
				PasswordGenerator
						.show(() -> SwingUtilities.invokeLater(() -> passwordGeneratorMenuItem.setEnabled(true)));
				break;
			case "filesync":
				filesyncMenuItem.setEnabled(false);
				FileSync.show(() -> SwingUtilities.invokeLater(() -> filesyncMenuItem.setEnabled(true)));
				break;
			case "publicKeyFolder":
				try {
					Desktop.getDesktop().open(PUBLIC_KEY_STORAGE.toFile());
				} catch (IOException ex) {
					ex.printStackTrace();
				}
				break;
			case "home":
				try {
					Desktop.getDesktop().browse(new URI("https://github.com/stud0709/oms_companion"));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				break;
			}
		}
	};

	/**
	 * Byte array to hex string adding divider (space) for readability
	 * 
	 * @param a
	 * @return
	 */
	public static String byteArrayToHex(byte[] a) {
		return byteArrayToHex(a, true);
	}

	public static String byteArrayToHex(byte[] a, boolean addDivider) {
		var sb = new StringBuilder(a.length * 2);
		for (int i = 0; i < a.length; i++) {
			sb.append(String.format("%02x", a[i])).append(i % 2 == 1 && addDivider ? " " : "");
		}
		return sb.toString().trim();
	}
}
