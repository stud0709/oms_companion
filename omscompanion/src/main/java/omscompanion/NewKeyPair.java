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
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.tr;

import java.awt.Color;
import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import j2html.tags.specialized.PTag;
import net.miginfocom.swing.MigLayout;

public class NewKeyPair {

	private JFrame frmNewKeyPair;
	private JTextField txtKeyAlias;
	private JLabel lblBackupFile;
	private JButton btnBackupFile;
	private JTextField txtBackupFile;
	private JPasswordField txtPassPhrase;
	private JTextField txtPassPhrase2;
	private JButton btnCreate;
	private JScrollPane scrollPane_Info;
	private JTextArea txtInfo;
	private JCheckBox chckbxStorePublicKey;
	private static final Color COLOR_RED = Color.decode("#F08080");
	private char echoChar;

	/**
	 * Create the application.
	 */
	public NewKeyPair() {
		initialize();

		// disable copy/paste for password fields
		txtPassPhrase.setTransferHandler(null);
		txtPassPhrase2.setTransferHandler(null);

		txtKeyAlias.getDocument().addDocumentListener(documentListener);
		txtPassPhrase.getDocument().addDocumentListener(documentListener);
		txtBackupFile.getDocument().addDocumentListener(documentListener);
		txtPassPhrase2.getDocument().addDocumentListener(documentListener);

		echoChar = txtPassPhrase.getEchoChar();
		txtPassPhrase.setEchoChar((char) 0);

		btnBackupFile.addActionListener(e -> {
			JFileChooser jfc = new JFileChooser() {
				private static final long serialVersionUID = 1L;

				@Override
				public void approveSelection() {
					File f = getSelectedFile();
					if (f.exists()) {
						int result = JOptionPane.showConfirmDialog(this, "The file exists, overwrite?", "Existing file",
								JOptionPane.YES_NO_CANCEL_OPTION);
						switch (result) {
						case JOptionPane.YES_OPTION:
							super.approveSelection();
							break;
						case JOptionPane.CANCEL_OPTION:
							cancelSelection();
							break;
						}
					} else {
						super.approveSelection();
					}
				}

			};
			jfc.setFileFilter(new FileFilter() {

				@Override
				public String getDescription() {
					return "HTML Files (*.html)";
				}

				@Override
				public boolean accept(File f) {
					return f.isDirectory() || f.getName().toLowerCase().endsWith(".html");
				}
			});

			int result = jfc.showSaveDialog(frmNewKeyPair);

			if (result != JFileChooser.APPROVE_OPTION)
				return;

			String s = jfc.getSelectedFile().getAbsolutePath();
			if (!s.toLowerCase().endsWith(".html"))
				s += ".html";

			txtBackupFile.setText(s);
		});

		btnCreate.addActionListener(e -> createKeyPair());

		checkState(null);
	}

	private void createKeyPair() {
		SwingUtilities.invokeLater(() -> {
			txtInfo.setText("");
			btnCreate.setEnabled(false);
		});

		try {
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(Main.KEY_ALG_RSA);
			keyPairGenerator.initialize(Main.KEY_LENGTH);
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			RSAPublicKey rsaPublicKey = (RSAPublicKey) keyPair.getPublic();

			SwingUtilities.invokeLater(() -> txtInfo.append("Key pair generated (" + rsaPublicKey.getAlgorithm() + ", "
					+ Main.KEY_LENGTH + ", " + rsaPublicKey.getFormat() + ")\n"));

			IvParameterSpec iv = AESUtil.generateIv();
			byte[] salt = AESUtil.generateSalt();
			SecretKey secretKey = AESUtil.getSecretKeyFromPassword(new String(txtPassPhrase.getPassword()), salt);

			SwingUtilities.invokeLater(() -> txtInfo.append("AES initialized\n"));

			String alias = txtKeyAlias.getText().trim();

			String message = new AesEncryptedKeyPairTransfer(alias, keyPair.getPrivate(), null, secretKey, iv, salt)
					.getMessage();

			File backupFile = new File(txtBackupFile.getText());

			try (FileWriter fw = new FileWriter(backupFile)) {
				fw.write(NewKeyPair.getKeyBackupHtml(alias, Main.getFingerprint(rsaPublicKey), message));
			}

			SwingUtilities.invokeLater(() -> txtInfo.append("Backup file generated\n"));

			if (chckbxStorePublicKey.isSelected()) {
				String fn = alias + "." + rsaPublicKey.getFormat().toLowerCase();
				Path publicKeyPath = Main.PUBLIC_KEY_STORAGE.resolve(fn);

				if (Files.exists(publicKeyPath)) {
					// create backup of the old key
					RSAPublicKey pk = Main.getPublicKey(Files.readAllBytes(publicKeyPath));

					byte[] fingerprint = Main.getFingerprint(pk);

					String fn_backup = fn + "." + Main.byteArrayToHex(fingerprint).replaceAll("\\W", "") + ".bak";

					Path backupPath = Main.PUBLIC_KEY_STORAGE.resolve(fn_backup);

					Files.copy(publicKeyPath, backupPath);

					SwingUtilities.invokeLater(
							() -> txtInfo.append("WARNING: Overwriting existing public key file. Old file copied to: \""
									+ backupPath.toAbsolutePath().toString() + "\"\n"));
				}

				Files.write(publicKeyPath, rsaPublicKey.getEncoded());

				SwingUtilities.invokeLater(() -> txtInfo
						.append("Public key written to: \"" + publicKeyPath.toAbsolutePath().toString() + "\"\n"));
			}

			SwingUtilities.invokeLater(() -> txtInfo.append("Displaying QR sequence... "));

			new QRFrame(message, QRFrame.DELAY, () -> {
				SwingUtilities.invokeLater(() -> txtInfo.append("Showing backup file\n"));
				try {
					Desktop.getDesktop().open(backupFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
				SwingUtilities.invokeLater(() -> {
					txtInfo.append("Operation suffessfully completed\n");
					btnCreate.setEnabled(true);
				});
			}).setVisible(true);

		} catch (Exception ex) {
			ex.printStackTrace();
			SwingUtilities.invokeLater(() -> {
				txtInfo.append(ex.toString());
				btnCreate.setEnabled(true);
			});
		}
	}

	private DocumentListener documentListener = new DocumentListener() {

		@Override
		public void removeUpdate(DocumentEvent e) {
			checkState(e);
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			checkState(e);
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			checkState(e);
		}
	};

	private void checkState(DocumentEvent e) {
		boolean b = true;

		b = checkKeyAlias();
		b &= checkPassphrase();
		b &= checkPassphrase2(e);
		b &= checkBackupFile();

		btnCreate.setEnabled(b);
	}

	private boolean checkKeyAlias() {
		String s = txtKeyAlias.getText().trim();
		String msg = null;

		if (s.isEmpty())
			msg = "Key Alias is mandatory";

		if (s.length() > 50)
			msg = "Key Alias is too long, max. 50";

		// https://alvinalexander.com/java/java-uimanager-color-keys-list/
		txtKeyAlias.setBackground(msg == null ? UIManager.getColor("TextArea.background") : COLOR_RED);

		txtKeyAlias.setToolTipText(msg);
		return msg == null;
	}

	private boolean checkPassphrase() {
		String s = new String(txtPassPhrase.getPassword());
		String msg = null;

		if (s.isEmpty())
			msg = "Pass Phrase is mandatory";

		if (s.length() < 10)
			msg = "10 or more symbols needed. It will take just a couple of hours to brute force your pass phrase!";

		txtPassPhrase.setBackground(msg == null ? UIManager.getColor("TextArea.background") : COLOR_RED);

		txtPassPhrase.setToolTipText(msg);
		return msg == null;
	}

	private boolean checkPassphrase2(DocumentEvent e) {
		if (e != null && e.getDocument() == txtPassPhrase.getDocument()) {
			// password has changed, start over
			txtPassPhrase2.setText("");
		}

		String s = txtPassPhrase2.getText();
		String msg = null;

		if (s.isEmpty()) {
			msg = "Repeat pass phrase to prevent typos";
			txtPassPhrase.setEchoChar((char) 0); // show pass phrase
		} else {
			txtPassPhrase.setEchoChar(echoChar); // hide pass phrase

			if (!s.equals(new String(txtPassPhrase.getPassword())))
				msg = "Pass phrases don't match!";
		}

		txtPassPhrase2.setBackground(msg == null ? UIManager.getColor("TextArea.background") : COLOR_RED);

		txtPassPhrase2.setToolTipText(msg);
		return msg == null;
	}

	private boolean checkBackupFile() {
		String s = txtBackupFile.getText();
		String msg = null;

		if (s.isEmpty())
			msg = "Backup File Path is mandatory";

		btnBackupFile.setBackground(msg == null ? UIManager.getColor("Button.background") : COLOR_RED);

		btnBackupFile.setToolTipText(msg);
		return msg == null;
	}

	public JFrame getFrame() {
		return frmNewKeyPair;
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmNewKeyPair = new JFrame();
		frmNewKeyPair.setResizable(false);
		frmNewKeyPair.setTitle("New Key Pair");
		frmNewKeyPair.setBounds(100, 100, 785, 559);
		frmNewKeyPair.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frmNewKeyPair.getContentPane().setLayout(new MigLayout("", "[][grow][]", ""));

		JLabel lblKeyAlias = new JLabel("Key Alias");
		frmNewKeyPair.getContentPane().add(lblKeyAlias, "cell 0 0,alignx right,growy");

		txtKeyAlias = new JTextField();
		frmNewKeyPair.getContentPane().add(txtKeyAlias, "cell 1 0,grow");
		txtKeyAlias.setColumns(10);

		JLabel lblTransportPass = new JLabel("Transport Password");
		frmNewKeyPair.getContentPane().add(lblTransportPass, "cell 0 1,alignx trailing,growy");

		txtPassPhrase = new JPasswordField();
		frmNewKeyPair.getContentPane().add(txtPassPhrase, "flowx,cell 1 1,grow");

		JLabel lblTransportPass2 = new JLabel("Repeat Password");
		frmNewKeyPair.getContentPane().add(lblTransportPass2, "cell 0 2,alignx trailing,growy");

		txtPassPhrase2 = new JTextField();
		frmNewKeyPair.getContentPane().add(txtPassPhrase2, "flowx,cell 1 2,grow");

		lblBackupFile = new JLabel("Backup File");
		frmNewKeyPair.getContentPane().add(lblBackupFile, "cell 0 3,alignx trailing,growy");

		txtBackupFile = new JTextField();
		txtBackupFile.setEditable(false);
		frmNewKeyPair.getContentPane().add(txtBackupFile, "cell 1 3,grow");
		txtBackupFile.setColumns(10);

		btnBackupFile = new JButton("...");
		frmNewKeyPair.getContentPane().add(btnBackupFile, "cell 2 3");

		chckbxStorePublicKey = new JCheckBox("Store Public Key for later use");
		chckbxStorePublicKey.setSelected(true);
		frmNewKeyPair.getContentPane().add(chckbxStorePublicKey, "cell 1 4");

		btnCreate = new JButton("Create");
		btnCreate.setEnabled(false);
		frmNewKeyPair.getContentPane().add(btnCreate, "flowx,cell 1 5");

		scrollPane_Info = new JScrollPane();
		frmNewKeyPair.getContentPane().add(scrollPane_Info, "cell 1 6,grow");

		txtInfo = new JTextArea();
		txtInfo.setEditable(false);
		txtInfo.setText(
				"***** PLEASE READ THIS FIRST *****\r\n\r\nThis will create a new key pair (see https://en.wikipedia.org/wiki/Public-key_cryptography for more information). The public key is used to encrypt data, you can share it with anyone. Data encrypted by the public key can only be decrypted by the private key.\r\n\r\nOnce your private key is imported into your smartphone, it will be protected on the operation system or (if your phone has the Strongbox feature) hardware level by your fingerprint. It is not possible to extract the key from the device. Even OneMoreSecret app cannot access your private key directly, it instructs the operation system to decrypt your data instead. \r\n\r\nIf you uninstall OneMoreSecret from your device, reset your phone or get a new one, you will need the backup file to import your private key into the new device. The backup file contains a sequence of QR codes, that you can scan with your OneMoreSecret app. \r\n\r\nTo protect the private key from unauthorized access, it will be encrypted with the transport password. Feel free to choose a fairly long passphrase - you will need it only to import the password into your smartphone. Something like \"Alice was beginning to get very tired of sitting by her sister on the bank\". As with every password, you will have to enter it exactly as specified to access your private key.");
		txtInfo.setLineWrap(true);
		txtInfo.setWrapStyleWord(true);
		scrollPane_Info.setViewportView(txtInfo);
	}

	public static String getKeyBackupHtml(String alias, byte[] fingerprint, String message)
			throws NoSuchAlgorithmException, IOException, WriterException {
		PTag qrCodes = p();

		List<BitMatrix> list = QRUtil.getQrSequence(message, QRUtil.CHUNK_SIZE, QRUtil.BARCODE_SIZE);

		for (int i = 0; i < list.size(); i++) {
			try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				MatrixToImageWriter.writeToStream(list.get(i), "PNG", baos);
				baos.flush();
				qrCodes.with(table(tr(td(Integer.toString(i + 1)), td(
						img().withSrc("data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray()))
								.withStyle("width:" + (QRUtil.BARCODE_SIZE / 2) + "px;height:"
										+ (QRUtil.BARCODE_SIZE / 2) + "px;")))
						.withStyle("vertical-align: bottom;")).withStyle("display: inline-block;"));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

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
				p(b("Key alias: " + alias)), p(b("Fingerprint: " + Main.byteArrayToHex(fingerprint))),
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

}
