package omscompanion;

import static j2html.TagCreator.b;
import static j2html.TagCreator.body;
import static j2html.TagCreator.br;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.html;
import static j2html.TagCreator.img;
import static j2html.TagCreator.join;
import static j2html.TagCreator.li;
import static j2html.TagCreator.ol;
import static j2html.TagCreator.p;

import java.awt.AWTException;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JLabel;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import j2html.tags.specialized.PTag;

public class Main {
	public static final String KEY_ALG = "RSA";
	public static Path PUBLIC_KEY_STORAGE = new File("public").toPath();
	public static final int KEY_LENGTH = 2048;
	public static final String PUBLIC_KEY_FILE_TYPE = ".x509";

	public static void main(String[] args) throws Exception {
		Files.createDirectories(PUBLIC_KEY_STORAGE);
		initTrayIcon();
		ClipboardUtil.startClipboardCheck();
	}

	private static void initTrayIcon() throws IOException, AWTException {
//		https://docs.oracle.com/javase/tutorial/uiswing/misc/systemtray.html

		if (!SystemTray.isSupported())
			return;

		Dimension size = SystemTray.getSystemTray().getTrayIconSize();

		BufferedImage bi = ImageIO.read(Main.class.getResourceAsStream("qr-code.png"));
		Image image = bi.getScaledInstance(size.width, size.height, Image.SCALE_SMOOTH);
		TrayIcon trayIcon = new TrayIcon(image, "omsCompanion");

		PopupMenu menu = new PopupMenu();
		{
			MenuItem processClipboard = new MenuItem("Process Clipboard");
			processClipboard.setFont(new JLabel().getFont().deriveFont(Font.BOLD));
			processClipboard.setActionCommand("processClipboard");
			processClipboard.addActionListener(MENU_ACTION_LISTENER);
			menu.add(processClipboard);
		}

		{
			Menu crypto = new Menu("Cryptography...");

			{
				MenuItem newKey = new MenuItem("New Private Key");
				newKey.setActionCommand("newKeyPair");
				newKey.addActionListener(MENU_ACTION_LISTENER);
				crypto.add(newKey);
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
				ClipboardUtil.checkClipboard();
				break;
			case "newKeyPair":
				new NewKeyPair().getFrame().setVisible(true);
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
	public static int genKey(String keyalg, String alias, Path keystorePath, String storepass, int validity,
			int keysize, String dname) throws IOException, InterruptedException {

		String keytoolPath = new File(System.getProperty("java.home")).toPath().resolve("bin").resolve("keytool.exe")
				.toAbsolutePath().toString();

		String cmd = keytoolPath + " -genkey" + " -keyalg " + keyalg + " -alias " + alias + " -keystore "
				+ keystorePath.toAbsolutePath() + " -storepass " + storepass + " -validity " + validity + " -keysize "
				+ keysize + " -dname \"" + dname + "\"";

		System.out.println(cmd);

		Process keytool = Runtime.getRuntime().exec(cmd);
		return keytool.waitFor();
	}

	public static String getKeyBackupHtml(String alias, byte[] fingerprint, String message)
			throws NoSuchAlgorithmException, IOException, WriterException {
		PTag qrCodes = p();

		List<BitMatrix> list = QRUtil.getQrSequence(message, QRUtil.CHUNK_SIZE, QRUtil.BARCODE_SIZE);

		qrCodes.with(list.stream().map(m -> {
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				MatrixToImageWriter.writeToStream(m, "PNG", baos);
				baos.flush();
				return img().withSrc("data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray()))
						.withStyle("width:" + (QRUtil.BARCODE_SIZE / 2) + "px;height:" + (QRUtil.BARCODE_SIZE / 2)
								+ "px;");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			return null;
		}));

		int lineLength = 75;

		PTag messageChunks = p().withStyle("font-family:monospace;");
		int offset = 0;
		while (offset < message.length()) {
			String s = message.substring(offset, Math.min(offset + lineLength, message.length()));
			messageChunks.withText(s).with(br());
			offset += lineLength;
		}

		return html(body(h1("OneMoreSecret Private Key Backup"), p(b("Keep this file / printout in a secure location")),
				p("This is a hard copy of your Private Key for OneMoreSecret. It can be used to import your Private Key into a new device or after a reset of OneMoreSecret App. This document is encrypted with AES encryption, you will need your TRANSPORT PASSWORD to complete the import procedure."),
				h2("WARNING:"),
				p(join(b("DO NOT"), " share this document with other persons.", br(), b("DO NOT"),
						" provide its content to untrusted apps, on the Internet etc.", br(),
						"If you need to restore your Key, start OneMoreSecret App on your phone ", b("BY HAND"),
						" and scan the codes. ", b("DO NOT"), " trust unexpected prompts and pop-ups.", br(),
						b("THIS DOCUMENT IS THE ONLY WAY TO RESTORE YOUR PRIVATE KEY"))),
				p(b("Key alias: " + alias)), p(b("Fingerprint: " + byteArrayToHex(fingerprint))),
				p("Scan this with your OneMoreSecret App:"), qrCodes, h2("Long-Term Backup and Technical Details"),
				p("Base64 Encoded Message: "), messageChunks, p("Message format: java.io.DataOutputStream."),
				p("Message Contents: "),
				ol(/* 1 */li("Application Identifier: (int) 0 = AES Encrypted Key Pair Transfer"),
						/* 2 */li("Alias length (bytes): int"), /* 3 */li("Alias: String, utf-8"),
						/* 4 */li("Length Salt: int"), /* 5 */li("Salt: byte[]"), /* 6 */li("IV: byte[]"),
						/* 7 */li("Length Cipher Algorithm: int"), /* 8 */li("Cipher Algorithm: String, utf-8"),
						/* 9 */li("Length Key Algorithm: int"), /* 10 */li("Key Algorithm: String, utf-8"),
						/* 11 */li("Keyspec Length: int"), /* 12 */li("Keyspec Iterations: int"),
						/* 13 */li("Length Cipher Text: int"), /* 14 */li("Cipher Text: byte[] (see below)")),
				p("Private Key Information (encrypted within Cipher Text)"),
				ol(/* 1 */li("Key length: int"), /* 2 */li("Certificate Length: int"), /* 3 */li("Key Data: byte[]"),
						/* 4 */li("Certificate Data: byte[]"),
						/* 5 */li("SHA-256 over Private Key and Certificate: byte[]"))))
				.render();
	}

	public static byte[] getFingerprint(RSAPublicKey publicKey) throws NoSuchAlgorithmException {
		MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
		sha256.update(publicKey.getModulus().toByteArray());
		return sha256.digest(publicKey.getPublicExponent().toByteArray());
	}

	public static String byteArrayToHex(byte[] a) {
		StringBuilder sb = new StringBuilder(a.length * 2);
		for (byte b : a)
			sb.append(String.format("%02x", b)).append(" ");
		return sb.toString();
	}

	public RSAPublicKey getPublicKey(Path p) throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
		PublicKey publicKey = KeyFactory.getInstance(KEY_ALG)
				.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(p)));

		return (RSAPublicKey) publicKey;
	}
}
