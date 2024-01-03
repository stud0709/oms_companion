package omscompanion.openjfx;

import static j2html.TagCreator.b;
import static j2html.TagCreator.body;
import static j2html.TagCreator.br;
import static j2html.TagCreator.h1;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.html;
import static j2html.TagCreator.img;
import static j2html.TagCreator.join;
import static j2html.TagCreator.p;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.tr;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import javax.crypto.spec.IvParameterSpec;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.crypto.AESUtil;
import omscompanion.crypto.AesEncryptedPrivateKeyTransfer;
import omscompanion.crypto.RSAUtils;
import omscompanion.qr.QRUtil;

public class NewPrivateKey {
	@FXML
	private Button btnBrowse;

	@FXML
	private Button btnCreate;

	@FXML
	private CheckBox chkStorePublicKey;

	@FXML
	private Label lblBackupFile;

	@FXML
	private Label lblKeyAlias;

	@FXML
	private Label lblRepeatPassword;

	@FXML
	private Label lblTransportPassword;

	@FXML
	private TextArea txtAreaInfo;

	@FXML
	private TextField txtBackupFile;

	@FXML
	private TextField txtKeyAlias;

	@FXML
	private TextField txtRepeatPwd;

	@FXML
	private TextField txtTransportPwd;

	@FXML
	private CheckBox chk4096bit;

	public static final int BASE64_LINE_LENGTH = 75;
	private final Paint errorPaint = Color.RED;
	private Paint colorDef = null;
	private String transportPwd = "";
	private boolean enableRepeatPwdListener = true, enableTransportPwdListener = true;;

	@FXML
	void onBrowse(ActionEvent event) {
		Platform.runLater(() -> {
			var fileChooser = new FileChooser();
			fileChooser.setTitle("Private Key Backup File");
			fileChooser.getExtensionFilters().add(new ExtensionFilter("HTML files", "*.html"));
			var selectedFile = fileChooser.showSaveDialog(btnBrowse.getScene().getWindow());
			if (selectedFile != null) {
				var selectedPath = selectedFile.getAbsolutePath().toString();
				var s = selectedPath + (selectedPath.toLowerCase().endsWith(".html") ? "" : ".html");

				txtBackupFile.setText(s);
				checkState();
			}
		});
	}

	public void init() {
		colorDef = lblBackupFile.getTextFill();

		txtAreaInfo.setText(
				"***** PLEASE READ THIS FIRST *****\r\n\r\nThis will create a new key pair (see https://en.wikipedia.org/wiki/Public-key_cryptography for more information). The public key is used to encrypt data, you can share it with anyone. Data encrypted by the public key can only be decrypted by the private key.\r\n\r\nOnce your private key is imported into your smartphone, it will be protected on the operation system or (if your phone has the Strongbox feature) hardware level by your fingerprint. It is not possible to extract the key from the device. Even OneMoreSecret app cannot access your private key directly, it instructs the operation system to decrypt your data instead. \r\n\r\nIf you uninstall OneMoreSecret from your device, reset your phone or get a new one, you will need the backup file to import your private key into the new device. The backup file contains a sequence of QR codes, that you can scan with your OneMoreSecret app. \r\n\r\nTo protect the private key from unauthorized access, it will be encrypted with the transport password. Feel free to choose a fairly long passphrase - you will need it only to import the password into your smartphone. Something like \"Alice was beginning to get very tired of sitting by her sister on the bank\". As with every password, you will have to enter it exactly as specified to access your private key.");
		txtTransportPwd.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!enableTransportPwdListener)
				return;

			if (oldValue.matches("\\u2022+")) {
				// throw away old password
				Platform.runLater(() -> {
					var s = newValue.replaceAll("\\u2022", "");
					txtTransportPwd.setText(s);
					txtTransportPwd.positionCaret(s.length());
				});
				return;
			}

			enableRepeatPwdListener = false;
			// whatever changed here, clear passphrase2
			txtRepeatPwd.clear();
			enableRepeatPwdListener = true;

			transportPwd = newValue;

			checkState();
		});

		txtRepeatPwd.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!enableRepeatPwdListener)
				return;

			enableTransportPwdListener = false;
			if (newValue.isEmpty()) {
				// make password visible
				txtTransportPwd.setText(transportPwd);
			} else {
				// mask input
				txtTransportPwd.setText(transportPwd.replaceAll(".", "\u2022"));
			}

			enableTransportPwdListener = true;

			checkState();

		});

		txtKeyAlias.textProperty().addListener((observable, oldValue, newValue) -> checkState());

		checkState();
	}

	private void checkState() {
		boolean b = true;

		b = checkKeyAlias();
		b &= checkTransportPwd();
		b &= checkRepeatPwd();
		b &= checkBackupFile();

		btnCreate.setDisable(!b);

	}

	private boolean checkKeyAlias() {
		var s = txtKeyAlias.getText().trim();
		String msg = null;

		if (s.isEmpty())
			msg = "Key Alias is mandatory";

		if (s.length() > 50)
			msg = "Key Alias is too long, max. 50";

		lblKeyAlias.setTextFill(msg == null ? colorDef : errorPaint);

		lblKeyAlias.setTooltip(new Tooltip(msg));
		return msg == null;
	}

	private boolean checkTransportPwd() {
		String msg = null;

		if (transportPwd.isEmpty())
			msg = "Pass Phrase is mandatory";

		if (transportPwd.length() < 10)
			msg = "10 or more symbols needed. It will take just a couple of hours to brute force your pass phrase!";

		lblTransportPassword.setTextFill(msg == null ? colorDef : errorPaint);

		lblTransportPassword.setTooltip(new Tooltip(msg));
		return msg == null;
	}

	private boolean checkRepeatPwd() {
		var s = txtRepeatPwd.getText();
		String msg = null;

		if (s.isEmpty()) {
			msg = "Repeat pass phrase to prevent typos";
		} else {
			if (!s.equals(transportPwd))
				msg = "Pass phrases don't match!";
		}

		lblRepeatPassword.setTextFill(msg == null ? colorDef : errorPaint);

		lblRepeatPassword.setTooltip(new Tooltip(msg));
		return msg == null;
	}

	private boolean checkBackupFile() {
		var s = txtBackupFile.getText();
		String msg = null;

		if (s.isEmpty())
			msg = "Backup File Path is mandatory";

		lblBackupFile.setTextFill(msg == null ? colorDef : errorPaint);

		lblBackupFile.setTooltip(new Tooltip(msg));
		return msg == null;
	}

	@FXML
	void onCreate(ActionEvent event) {
		txtAreaInfo.setText("");
		btnCreate.setDisable(true);

		new Thread(() -> {
			try {
				var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
				var rsaKeyLength = chk4096bit.isSelected() ? 4096 : 2048;
				keyPairGenerator.initialize(rsaKeyLength);
				var keyPair = keyPairGenerator.generateKeyPair();
				var rsaPublicKey = (RSAPublicKey) keyPair.getPublic();

				Platform.runLater(() -> txtAreaInfo.appendText(String.format("Key pair generated (%s, %s, %s)\n",
						rsaPublicKey.getAlgorithm(), rsaKeyLength, rsaPublicKey.getFormat())));

				IvParameterSpec iv = AESUtil.generateIv();
				System.out.println("iv: " + Main.byteArrayToHex(iv.getIV()));

				var salt = AESUtil.generateSalt(AESUtil.getSaltLength());
				System.out.println("salt: " + Main.byteArrayToHex(salt));

				var aesKeyLength = AESUtil.getKeyLength();
				System.out.println("key length: " + aesKeyLength);

				var aesKeyspecIterations = AESUtil.getKeyspecIterations();
				System.out.println("iterations: " + aesKeyspecIterations);

				var keyAlgorithm = AESUtil.getAesKeyAlgorithm().keyAlgorithm;
				System.out.println("key algorithm: " + keyAlgorithm);

				var secretKey = AESUtil.getSecretKeyFromPassword(transportPwd.toCharArray(), salt, keyAlgorithm,
						aesKeyLength, aesKeyspecIterations);

				Platform.runLater(() -> txtAreaInfo.appendText("AES initialized\n"));

				var alias = txtKeyAlias.getText().trim();

				var message = new AesEncryptedPrivateKeyTransfer(alias, keyPair, secretKey, iv, salt,
						AESUtil.getTransformationIdx(), AESUtil.getAesKeyAlgorithmIdx(), aesKeyLength,
						aesKeyspecIterations).getMessage();

				var backupFile = new File(txtBackupFile.getText());

				try (var fw = new FileWriter(backupFile)) {
					fw.write(NewPrivateKey.getKeyBackupHtml(alias, RSAUtils.getFingerprint(rsaPublicKey), message));
				}

				txtAreaInfo.appendText("Backup file generated\n");

				if (chkStorePublicKey.isSelected()) {
					var p = PublicKeyImport.savePublicKey(alias, rsaPublicKey, backupPath -> {
						Platform.runLater(() -> txtAreaInfo.appendText(
								String.format("WARNING: Overwriting existing public key file. Old file copied to: %s\n",
										backupPath.toAbsolutePath())));
						return true;
					});

					Platform.runLater(() -> txtAreaInfo
							.appendText(String.format("Public key written to: %s\n", p.toAbsolutePath())));
				}

				Platform.runLater(() -> txtAreaInfo.appendText("Displaying QR sequence\n"));

				QRFrame.showForMessage(message, false, false, s -> {
					Platform.runLater(() -> txtAreaInfo.appendText("Showing backup file\n"));
					try {
						Desktop.getDesktop().open(backupFile);
					} catch (IOException e) {
						FxMain.handleException(e);
					}
					Platform.runLater(() -> {
						txtAreaInfo.appendText("Operation suffessfully completed\n");
						btnCreate.setDisable(false);
					});
				});

			} catch (Exception ex) {
				FxMain.handleException(ex);
				btnCreate.setDisable(false);
			}
		}).start();
	}

	public static String getKeyBackupHtml(String alias, byte[] fingerprint, byte[] message)
			throws NoSuchAlgorithmException, IOException, WriterException {
		var qrCodes = p();

		var list = QRUtil.getQrSequence(MessageComposer.encodeAsOmsText(message).toCharArray(), QRUtil.getChunkSize(),
				QRUtil.getBarcodeSize());

		for (int i = 0; i < list.size(); i++) {
			try (var baos = new ByteArrayOutputStream()) {
				MatrixToImageWriter.writeToStream(list.get(i), "PNG", baos);
				baos.flush();
				qrCodes.with(table(tr(td(Integer.toString(i + 1)), td(
						img().withSrc("data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray()))
								.withStyle("width:" + (QRUtil.getBarcodeSize() / 2) + "px;height:"
										+ (QRUtil.getBarcodeSize() / 2) + "px;")))
						.withStyle("vertical-align: bottom;")).withStyle("display: inline-block;"));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		var messageAsOmsText = MessageComposer.encodeAsOmsText(message);
		var messageChunks = p().withStyle("font-family:monospace;");
		var offset = 0;
		while (offset < messageAsOmsText.length()) {
			var s = messageAsOmsText.substring(offset,
					Math.min(offset + BASE64_LINE_LENGTH, messageAsOmsText.length()));
			messageChunks.withText(s).with(br());
			offset += BASE64_LINE_LENGTH;
		}

		return html(body(h1("OneMoreSecret Private Key Backup"), p(b("Keep this file / printout in a secure location")),
				p("This is a hard copy of your Private Key for OneMoreSecret. "
						+ "It can be used to import your Private Key into a new device "
						+ "or after a reset of OneMoreSecret App. This document is encrypted with AES, "
						+ "you will need your TRANSPORT PASSWORD to complete the import procedure."),
				h2("WARNING:"),
				p(join("DO NOT share this document with other persons.", br(),
						"DO NOT provide its content to untrusted apps, on the Internet etc.", br(),
						"If you need to restore your Key, start OneMoreSecret App on your phone BY HAND and scan the codes. ",
						"DO NOT trust unexpected prompts and pop-ups.", br(),
						b("THIS DOCUMENT IS THE ONLY WAY TO RESTORE YOUR PRIVATE KEY"))),
				p(b("Key alias: " + alias)), p(b("RSA Fingerprint: " + Main.byteArrayToHex(fingerprint))),
				p("Scan this with your OneMoreSecret App:"), qrCodes, h2("Long-Term Backup and Technical Details"),
				p("Base64 Encoded Message: "), messageChunks,
				p("Message format: " + MessageComposer.OMS_PREFIX + "[base64 encoded data]"),
				p("Data format: see https://github.com/stud0709/oms_companion/blob/master/omscompanion/src/main/java/omscompanion/crypto/AesEncryptedPrivateKeyTransfer.java")))
				.render();
	}

	public static void show(Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/NewPrivateKey.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((NewPrivateKey) fxmlLoader.getController()).init();
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": New Private Key");
				stage.getIcons().add(FxMain.getImage("qr-code"));
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					if (andThen != null)
						andThen.run();
				});
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

}
