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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
	public static final String KEY_ALG_RSA = "RSA";
	public static Path PUBLIC_KEY_STORAGE = new File("public").toPath();
	public static final int KEY_LENGTH = 2048;

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
				ClipboardUtil.processClipboard();
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

		String messageAsUrl = MessageComposer.asURL(message);
		PTag messageChunks = p().withStyle("font-family:monospace;");
		int offset = 0;
		while (offset < messageAsUrl.length()) {
			String s = messageAsUrl.substring(offset, Math.min(offset + lineLength, messageAsUrl.length()));
			messageChunks.withText(s).with(br());
			offset += lineLength;
		}

		String sArr[] = message.split("\t");

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
				p("Base64 Encoded Message: "), messageChunks, p("Message format: oms://[base64 encoded data]"),
				p("Data format: String (utf-8), separator: TAB"), p("Data elements:"),
				ol(/* 1 */li("Application Identifier: " + sArr[0] + " = AES Encrypted Key Pair Transfer"),
						/* 2 */li("Key Alias = " + sArr[1]), /* 3 */li("Salt: base64-encoded byte[]"),
						/* 4 */li("IV: base64-encoded byte[]"), /* 5 */li("Cipher Algorithm = " + sArr[4]),
						/* 6 */li("Key Algorithm = " + sArr[5]), /* 7 */li("Keyspec Length = " + sArr[6]),
						/* 8 */li("Keyspec Iterations = " + sArr[7]),
						/* 9 */li("Cipher Text: base64-encoded byte[] (see below)")),
				p("Private Key Information (encrypted within Cipher Text. String (utf-8), separator: TAB)"),
				ol(/* 1 */li("Key Data: base64-encoded byte[]"), /* 2 */li("Certificate Data: base64-encoded byte[]"),
						/* 3 */li("SHA-256 over Private Key and Certificate: base64-encoded byte[]"))))
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

	public static RSAPublicKey getPublicKey(byte[] encoded) throws InvalidKeySpecException, NoSuchAlgorithmException {
		PublicKey publicKey = KeyFactory.getInstance(KEY_ALG_RSA).generatePublic(new X509EncodedKeySpec(encoded));

		return (RSAPublicKey) publicKey;
	}
}
