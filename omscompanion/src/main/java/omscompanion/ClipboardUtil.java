package omscompanion;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Base64;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import omscompanion.crypto.KeyRequest;
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

				QRFrame.showForMessage(message, true, false, _s -> {
					set(""); // clear the clipboard
					resumeClipboardCheck();
				});

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

			if (listOfFile != null && !listOfFile.isEmpty()) {

				if (listOfFile.size() > 1) {
					Platform.runLater(() -> {
						var alert = new Alert(AlertType.ERROR);
						alert.setTitle("Decrypt Files");
						alert.setHeaderText(String.format("Clipboard contains %s files", listOfFile.size()));
						alert.setContentText("Only single files can be decrypted via Air Gap");
						alert.showAndWait();
					});
					return;
				}

				File f = listOfFile.get(0);

				if (f.isDirectory()) {
					Platform.runLater(() -> {
						var alert = new Alert(AlertType.ERROR);
						alert.setTitle("Decrypt Files");
						alert.setHeaderText(String.format("Clipboard contains a directory", listOfFile.size()));
						alert.setContentText("Only single files can be decrypted via Air Gap");
						alert.showAndWait();
					});
					return;
				}

				onFile(f);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void onFile(File encryptedFile) throws NoSuchAlgorithmException, IOException {
		suspendClipboardCheck();

		var keyRequest = new KeyRequest(encryptedFile);

		QRFrame.showForMessage(keyRequest.getMessage(), false, true, s -> {
			// the base64 encoded data might contain minus sign
			// as separator and lack the padding "="

			s = s.replaceAll("-", "");
			while (s.length() % 4 != 0) {
				s += "=";
			}

			var keyResponse = Base64.decode(s);

			var decryptedFileName = encryptedFile.getName().substring(0,
					encryptedFile.getName().length() - (MessageComposer.OMS_FILE_TYPE.length() + 1 /* the dot */));

			var fileType = getFileType(decryptedFileName);

			Platform.runLater(() -> {
				var fileChooser = new FileChooser();
				fileChooser.setTitle("Private Key Backup File");
				if (fileType != null)
					fileChooser.getExtensionFilters()
							.add(new ExtensionFilter(fileType.toUpperCase() + " files", "*." + fileType.toLowerCase()));
				var selectedFile = fileChooser.showSaveDialog(FxMain.getPrimaryState());

				if (selectedFile != null) {
					var selectedPath = selectedFile.getAbsolutePath().toString();

					selectedPath = selectedPath
							+ (selectedPath.toLowerCase().endsWith("." + fileType.toLowerCase()) ? ""
									: "." + fileType.toLowerCase());

					var outfile = new File(selectedPath);

					var confirmationDialog = new Alert(AlertType.CONFIRMATION);
					confirmationDialog.setTitle("File already exists");
					confirmationDialog.setHeaderText(String.format("Do you want to overwrite %s", outfile.getName()));

					var yesButton = new ButtonType("Yes");
					var noButton = new ButtonType("No");
					confirmationDialog.getButtonTypes().setAll(yesButton, noButton);

					var result = confirmationDialog.showAndWait();

					if (result.isPresent() && result.get() == yesButton) {
						new Thread(() -> {
							try (FileOutputStream fos = new FileOutputStream(outfile)) {
								keyRequest.onReply(encryptedFile, fos, keyResponse);

								Platform.runLater(() -> {
									var doneDialog = new Alert(AlertType.CONFIRMATION);
									doneDialog.setTitle("Success!");
									doneDialog.setHeaderText(
											String.format("%s was created, what's next?", outfile.getName()));

									var folderButton = new ButtonType("Open Folder");
									var runButton = new ButtonType("Open File");
									var closeButton = new ButtonType("Done!");
									confirmationDialog.getButtonTypes().setAll(folderButton, runButton, closeButton);

									var doneResult = confirmationDialog.showAndWait();

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
									}
								});
							} catch (Exception ex) {
								ex.printStackTrace();

								Platform.runLater(() -> {
									var alertDialog = new Alert(AlertType.WARNING);
									alertDialog.setTitle("Could not create file");
									alertDialog.setHeaderText(ex.getMessage());
									alertDialog.showAndWait();
								});
							}
						}).start();
					}
				}
			});
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
