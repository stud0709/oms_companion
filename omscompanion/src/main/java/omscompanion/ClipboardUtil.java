package omscompanion;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Base64;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedFile;
import omscompanion.crypto.KeyRequest;
import omscompanion.crypto.RSAUtils;
import omscompanion.openjfx.EncryptionToolBar;
import omscompanion.openjfx.NewItem;
import omscompanion.openjfx.QRFrame;

public class ClipboardUtil {
	private static Thread t = null;
	private static final AtomicBoolean CHECK_CLIPBOARD = new AtomicBoolean(false);
	private static final SimpleBooleanProperty automaticModeProperty = new SimpleBooleanProperty(false);
	private static final String PROP_AUTO_CLIPBOARD_CHECK = "auto_clipboard_check",
			PROP_CLIPBOARD_CHECK_INTERVAL = "clipboard_check_interval";

	public static void init() {
		automaticModeProperty.addListener((observable, oldValue, newValue) -> {
			Main.properties.setProperty(PROP_AUTO_CLIPBOARD_CHECK, Boolean.toString(newValue));

			CHECK_CLIPBOARD.set(newValue);

			if (newValue)
				startClipboardCheck();

		});

		boolean autoCheckClipboard = Boolean
				.parseBoolean(Main.properties.getProperty(PROP_AUTO_CLIPBOARD_CHECK, "true"));
		automaticModeProperty.set(autoCheckClipboard);
	}

	/**
	 * This property defines if automatic clipboard check is activated. This can be
	 * suspended by {@link ClipboardUtil#suspendClipboardCheck()}.
	 * 
	 * @return
	 */
	public static SimpleBooleanProperty getAutomaticModeProperty() {
		return automaticModeProperty;
	}

	/**
	 * Suspends clipboard check until {@link #resumeClipboardCheck()} has been
	 * called.
	 */
	public static void suspendClipboardCheck() {
		CHECK_CLIPBOARD.set(false);
	}

	/**
	 * Resumes clipboard check if generally enabled.
	 * 
	 * @see ClipboardUtil#automaticModeProperty
	 */
	public static void resumeClipboardCheck() {
		CHECK_CLIPBOARD.set(automaticModeProperty.get());
	}

	public static void set(String s) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(s), null);
	}

	private static long getCheckInterval() {
		return Long.parseLong(Main.properties.getProperty(PROP_CLIPBOARD_CHECK_INTERVAL, "1000"));
	}

	public static void set(File... fArr) {
		var files = Arrays.stream(fArr).collect(Collectors.toList());
		var ft = new FileTransferable(files);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(ft, null);
	}

	public static synchronized void checkClipboard(CompletableFuture<Boolean> result) {
		Platform.runLater(() -> {
			try {
				var s = Clipboard.getSystemClipboard().getString();

				if (s == null) {
					result.complete(false);
					return;
				}

				s = s.trim();

				var message = MessageComposer.decode(s);

				if (message == null) {
					// not a valid OMS message
					result.complete(false);
					return;
				}

				suspendClipboardCheck();

				QRFrame.showForMessage(message, true, false, _s -> Platform.runLater(() -> {
					set(""); // clear the clipboard
					resumeClipboardCheck();
				}));

				result.complete(true);
			} catch (Exception e) {
				e.printStackTrace();
			}

			result.complete(false);
		});
	}

	protected static synchronized void startClipboardCheck() {
		if (t != null)
			return;

		t = new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					Thread.sleep(getCheckInterval());
				} catch (InterruptedException e) {
					return;
				}

				if (!CHECK_CLIPBOARD.get())
					continue;

				var future = new CompletableFuture<Boolean>();

				checkClipboard(future);
			}

			t = null;
		});

		t.setDaemon(true);

		t.start();
	}

	public static void processClipboard() {
		try {
			var future = new CompletableFuture<Boolean>();
			ClipboardUtil.checkClipboard(future);

			if (future.get()) // found oms:// in the clipboard
				return;

			// try to encrypt clipboard content
			var futureString = new CompletableFuture<String>();
			Platform.runLater(() -> {
				futureString.complete(Clipboard.getSystemClipboard().getString());
			});

			var s = futureString.get();

			if (s != null && !s.trim().isEmpty()) {
				suspendClipboardCheck();

				NewItem.showForMessage(s.getBytes(), () -> {
					set(""); // clear the clipboard
					resumeClipboardCheck();
				});

				return;
			}

			var futureLoF = new CompletableFuture<List<File>>();
			Platform.runLater(() -> {
				futureLoF.complete(Clipboard.getSystemClipboard().getFiles());

			});

			var listOfFile = futureLoF.get();

			if (listOfFile == null || listOfFile.isEmpty())
				return;

			suspendClipboardCheck();

			var containsOmsFile = listOfFile.stream()
					.anyMatch(f -> f.getName().endsWith(".".concat(MessageComposer.OMS_FILE_TYPE)));

			var containsOther = listOfFile.stream()
					.anyMatch(f -> f.isDirectory() || !f.getName().endsWith(".".concat(MessageComposer.OMS_FILE_TYPE)));

			if (containsOmsFile && containsOther) {
				Platform.runLater(() -> {
					var alert = new Alert(AlertType.ERROR);
					alert.setTitle("Invalid Clipboard Contents");
					alert.setHeaderText("Clipboard contains encrypted and unencrypted data");
					alert.setContentText("We cannot process a mix of both");
					alert.showAndWait();
					resumeClipboardCheck();
				});
				return;
			}

			if (containsOmsFile) {
				if (listOfFile.size() > 1) {
					Platform.runLater(() -> {
						var alert = new Alert(AlertType.ERROR);
						alert.setTitle("Decrypt Files");
						alert.setHeaderText(String.format("Clipboard contains %s files", listOfFile.size()));
						alert.setContentText("Only single files can be decrypted via Air Gap");
						alert.showAndWait();
						resumeClipboardCheck();
					});
					return;
				}

				onEncryptedFile(listOfFile.get(0));
			} else {
				encrypt(listOfFile);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void encrypt(List<File> listOfFile) throws Exception {
		try {
			var choiceBox = new ChoiceBox<String>();
			EncryptionToolBar.initChoiceBox(choiceBox);
			choiceBox.setPrefWidth(Double.MAX_VALUE);
			var pkFuture = new CompletableFuture<String>();

			Platform.runLater(() -> {
				var vbox = new VBox();
				vbox.setPrefWidth(480);
				vbox.getChildren().addAll(choiceBox);

				var keySelector = new Alert(AlertType.CONFIRMATION);
				keySelector.setTitle("Encrypt files");
				keySelector.setHeaderText("Select public key to encrypt your files");
				keySelector.getDialogPane().setContent(vbox);

				var okButton = new ButtonType("OK");
				var cancelButton = new ButtonType("Cancel");
				keySelector.getButtonTypes().setAll(okButton, cancelButton);

				var result = keySelector.showAndWait().orElse(cancelButton);

				if (result != okButton)
					pkFuture.complete(null);

				pkFuture.complete(choiceBox.getSelectionModel().getSelectedItem());
			});

			if (pkFuture.get() == null) {
				resumeClipboardCheck();
				return;
			}

			var pkPath = Main.PUBLIC_KEY_STORAGE.resolve(choiceBox.getSelectionModel().getSelectedItem());
			var pk = RSAUtils.getPublicKey(Files.readAllBytes(pkPath));

			if (listOfFile.size() > 1 || listOfFile.get(0).isDirectory()) {
				// TODO: show "Save as" for target directory
			} else {
				// encrypt into temporary directory
				var oFileName = listOfFile.get(0).getName().concat(".").concat(MessageComposer.OMS_FILE_TYPE);
				File oFile = Main.TMP.resolve(oFileName).toFile();

				try (var fis = new FileInputStream(listOfFile.get(0))) {
					EncryptedFile.create(fis, oFile, pk, RSAUtils.getRsaTransformationIdx(), AESUtil.getKeyLength(),
							AESUtil.getAesTransformationIdx());
				}

				oFile.deleteOnExit();

				ClipboardUtil.set(oFile);

				Platform.runLater(() -> {
					var alertDialog = new Alert(AlertType.INFORMATION);
					alertDialog.setTitle("File successfully encrypted");
					alertDialog.setHeaderText(String.format("%s has been set to the clipboard", oFileName));
					alertDialog.showAndWait();
					resumeClipboardCheck();
				});
			}
		} catch (Exception ex) {
			Platform.runLater(() -> {
				var alertDialog = new Alert(AlertType.WARNING);
				alertDialog.setTitle("Could not encrypt file");
				alertDialog.setHeaderText(ex.getMessage());
				alertDialog.showAndWait();
				resumeClipboardCheck();
			});
		}
	}

	private static void onEncryptedFile(File encryptedFile) throws NoSuchAlgorithmException, IOException {

		var keyRequest = new KeyRequest(encryptedFile);

		QRFrame.showForMessage(keyRequest.getMessage(), false, true, s -> {
			set("");

			if (s.isEmpty()) {
				resumeClipboardCheck();
				return;
			}

			var keyResponse = Base64.decode(s);

			var decryptedFileName = encryptedFile.getName().substring(0,
					encryptedFile.getName().length() - (MessageComposer.OMS_FILE_TYPE.length() + 1 /* the dot */));

			var fileType = getFileType(decryptedFileName);
			var dialogResult = new CompletableFuture<File>();

			selectOutFile(dialogResult, fileType);

			try {
				File outfile = dialogResult.get();

				if (outfile != null) {
					try (FileOutputStream fos = new FileOutputStream(outfile)) {
						keyRequest.onReply(fos, keyResponse);

						Platform.runLater(() -> {
							var doneDialog = new Alert(AlertType.CONFIRMATION);
							doneDialog.setTitle("Success!");
							doneDialog.setHeaderText(String.format("%s was created, what's next?", outfile.getName()));

							var folderButton = new ButtonType("Open Folder");
							var runButton = new ButtonType("Open File");
							var closeButton = new ButtonType("Done!");
							doneDialog.getButtonTypes().setAll(folderButton, runButton, closeButton);

							var doneResult = doneDialog.showAndWait();

							if (doneResult.isPresent()) {
								var nextAction = doneResult.get();

								try {
									if (nextAction == folderButton) {
										Desktop.getDesktop().open(outfile.getParentFile());
									}
									if (nextAction == runButton) {
										Desktop.getDesktop().open(outfile);
									}
								} catch (IOException e) {
									e.printStackTrace();
								}

								resumeClipboardCheck();
							}
						});
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();

				Platform.runLater(() -> {
					var alertDialog = new Alert(AlertType.WARNING);
					alertDialog.setTitle("Could not create file");
					alertDialog.setHeaderText(ex.getMessage());
					alertDialog.showAndWait();
					resumeClipboardCheck();
				});
			}
		});
	}

	private static void selectOutFile(CompletableFuture<File> dialogResult, String fileType) {
		Platform.runLater(() -> {
			var fileChooser = new FileChooser();
			fileChooser.setTitle("Decrypt And Save As...");
			if (fileType != null)
				fileChooser.getExtensionFilters()
						.add(new ExtensionFilter(fileType.toUpperCase() + " files", "*." + fileType.toLowerCase()));
			var selectedFile = fileChooser.showSaveDialog(FxMain.getPrimaryState());

			if (selectedFile == null) {
				dialogResult.complete(null);
			} else {
				var selectedPath = selectedFile.getAbsolutePath().toString();

				selectedPath = selectedPath + (selectedPath.toLowerCase().endsWith("." + fileType.toLowerCase()) ? ""
						: "." + fileType.toLowerCase());

				var outfile = new File(selectedPath);

				var yesButton = new ButtonType("Yes");
				var result = Optional.of(yesButton);

				if (Files.exists(outfile.toPath())) {
					var confirmationDialog = new Alert(AlertType.CONFIRMATION);
					confirmationDialog.setTitle("File already exists");
					confirmationDialog.setHeaderText(String.format("Do you want to overwrite %s", outfile.getName()));

					var noButton = new ButtonType("No");
					confirmationDialog.getButtonTypes().setAll(yesButton, noButton);
					result = confirmationDialog.showAndWait();
				}

				if (result.isPresent() && result.get() == yesButton) {
					dialogResult.complete(outfile);
				} else {
					dialogResult.complete(null);
				}
			}
		});
	}

	private static String getFileType(String filename) {
		String fileType = null;

		var dotIndex = filename.lastIndexOf(".");

		if (dotIndex > 0 && dotIndex < filename.length() - 1) {
			fileType = filename.substring(dotIndex + 1);
		}

		return fileType;
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
