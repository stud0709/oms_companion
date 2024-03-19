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
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.bouncycastle.util.encoders.Base64;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedFile;
import omscompanion.crypto.KeyRequest;
import omscompanion.crypto.PairingInfo;
import omscompanion.crypto.RSAUtils;
import omscompanion.openjfx.NewItem;
import omscompanion.openjfx.QRFrame;
import omscompanion.openjfx.RSAPublicKeyItem;

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
					return;
				}

				s = s.trim();

				var message = MessageComposer.decode(s);

				if (message == null) {
					// not a valid OMS message
					return;
				}

				suspendClipboardCheck();

				if (PairingInfo.getInstance() == null) {
					QRFrame.showForMessage(message, true, false, null, _s -> Platform.runLater(() -> {
						set(""); // clear the clipboard
						resumeClipboardCheck();
					}));
				} else {
					// send via network instead of QR code
					PairingInfo.getInstance().sendMessage(message, bArr -> {
						try {
							if (bArr == null)
								return; // no reply available

							if (bArr.length == 0)
								return;

							// TODO: process reply
						} finally {
							Platform.runLater(() -> {
								set(""); // clear the clipboard
								resumeClipboardCheck();
							});
						}
					});
				}

				result.complete(true);
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (!result.isDone())
					result.complete(false);
			}
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
			set(""); // clear the clipboard

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
					alert.setTitle(Main.APP_NAME);
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
						alert.setTitle(Main.APP_NAME);
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
			var choiceBox = new ChoiceBox<RSAPublicKeyItem>();
			FxMain.initChoiceBox(choiceBox, Main.getDefaultKeyAlias());
			choiceBox.setPrefWidth(Double.MAX_VALUE);
			var pkFuture = new CompletableFuture<RSAPublicKey>();

			if (choiceBox.getItems().size() == 1) {
				// there is only one key
				pkFuture.complete(choiceBox.getItems().get(0).publicKey);
			} else {
				// show dialog
				Platform.runLater(() -> {
					var vbox = new VBox();
					vbox.setPrefWidth(480);
					vbox.getChildren().addAll(choiceBox);

					var keySelector = new Alert(AlertType.CONFIRMATION);
					keySelector.setTitle(Main.APP_NAME);
					keySelector.setHeaderText("Select public key to encrypt your files");
					keySelector.getDialogPane().setContent(vbox);

					var okButton = new ButtonType("OK");
					var cancelButton = new ButtonType("Cancel");
					keySelector.getButtonTypes().setAll(okButton, cancelButton);

					var result = keySelector.showAndWait().orElse(cancelButton);

					if (result != okButton)
						pkFuture.complete(null);

					pkFuture.complete(choiceBox.getSelectionModel().getSelectedItem().publicKey);
				});
			}

			var pk = pkFuture.get();

			if (pk == null) {
				resumeClipboardCheck();
				return;
			}

			if (listOfFile.size() > 1 || listOfFile.get(0).isDirectory()) {
				var targetDirFuture = new CompletableFuture<File>();
				Platform.runLater(() -> {
					var dirChooser = new DirectoryChooser();
					dirChooser.setTitle("Target directory for encrypted files");
					dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
					targetDirFuture.complete(dirChooser.showDialog(FxMain.getPrimaryStage()));
				});

				var targetDir = targetDirFuture.get();
				if (targetDir == null) {
					resumeClipboardCheck();
					return;
				}

				var totalLength = new AtomicLong();
				var fileCnt = new AtomicInteger();
				var executorService = Executors.newFixedThreadPool(10);
				var cancelled = new AtomicBoolean(false);

				listOfFile.stream().forEach(source -> {
					encryptAndMirror(source, targetDir.toPath(), pk, executorService, () -> cancelled.get(), e -> {
					}, (f, p) -> {
						totalLength.addAndGet(f.length());
						fileCnt.incrementAndGet();
					}, (f, p) -> {
					}, true);
				});

				var targetLength = totalLength.get();
				totalLength.set(0);
				var progressCounter = new SimpleDoubleProperty();

				var progressBar = new ProgressBar();
				progressBar.progressProperty().bind(progressCounter);

				var dlg = new CompletableFuture<Alert>();

				Platform.runLater(() -> {
					var progressDialog = new Alert(AlertType.INFORMATION);
					dlg.complete(progressDialog);
					progressDialog.setTitle(Main.APP_NAME);
					var cancelButton = new ButtonType("Cancel");
					progressDialog.getButtonTypes().setAll(cancelButton);
					progressDialog.setHeaderText(String.format("Encrypting %d files / %d kB, please wait...",
							fileCnt.get(), (targetLength / 1024)));
					progressDialog.getDialogPane().setContent(progressBar);
					progressDialog.showAndWait();

					// cancel process
					cancelled.set(true);
				});

				var progressDialog = dlg.get();
				var errorCnt = new AtomicInteger();
				var dirCnt = new AtomicInteger();

				listOfFile.stream().forEach(source -> {
					encryptAndMirror(source, targetDir.toPath(), pk, executorService, () -> cancelled.get(), e -> {
						errorCnt.incrementAndGet();
						e.printStackTrace();
					}, (f, p) -> {
						System.out.println(f.getAbsolutePath());
						progressCounter.set((double) totalLength.addAndGet(f.length()) / (double) targetLength);
					}, (f, p) -> dirCnt.incrementAndGet(), false);
				});

				System.out.println(cancelled.get() ? "cancelled" : "done");

				executorService.shutdown();
				executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

				System.out.println("all tasks have finished");

				if (cancelled.get()) {
					resumeClipboardCheck();
					return;
				}

				Platform.runLater(() -> progressDialog.close());

				Platform.runLater(() -> {
					var doneDialog = new Alert(AlertType.CONFIRMATION);
					doneDialog.setTitle(Main.APP_NAME);
					doneDialog.setHeaderText(String.format("%s files, %s directories, %s errors. What's next?",
							fileCnt.get(), dirCnt.get(), errorCnt.get()));

					var folderButton = new ButtonType("Open Folder");
					var closeButton = new ButtonType("Done!");
					doneDialog.getButtonTypes().setAll(folderButton, closeButton);

					var doneResult = doneDialog.showAndWait();

					if (doneResult.isPresent()) {
						var nextAction = doneResult.get();

						try {
							if (nextAction == folderButton) {
								Desktop.getDesktop().open(targetDir);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

					resumeClipboardCheck();
				});
			} else {
				// encrypt into temporary directory
				var oFileName = listOfFile.get(0).getName().concat(".").concat(MessageComposer.OMS_FILE_TYPE);
				File oFile = Main.TMP.resolve(oFileName).toFile();

				try (var fis = new FileInputStream(listOfFile.get(0))) {
					EncryptedFile.create(fis, oFile, pk, RSAUtils.getTransformationIdx(), AESUtil.getKeyLength(),
							AESUtil.getTransformationIdx(), null);
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
			ex.printStackTrace();

			Platform.runLater(() -> {
				var alertDialog = new Alert(AlertType.WARNING);
				alertDialog.setTitle(Main.APP_NAME);
				alertDialog.setHeaderText(String.format("Could not encrypt file: %s", ex.getMessage()));
				alertDialog.showAndWait();
				resumeClipboardCheck();
			});
		}
	}

	private static void encryptAndMirror(File f, Path targetDir, RSAPublicKey pk, ExecutorService executorService,
			Supplier<Boolean> cancelled, Consumer<Exception> onError, BiConsumer<File, Path> onFile,
			BiConsumer<File, Path> onDir, boolean simulate) {

		if (cancelled.get())
			return;

		try {
			if (f.isFile()) {
				// encrypt the file
				var mirrorPath = targetDir.resolve(f.getName().concat(".").concat(MessageComposer.OMS_FILE_TYPE));
				if (simulate) {
					onFile.accept(f, mirrorPath);
				} else {
					executorService.submit(() -> {
						try (var fis = new FileInputStream(f)) {
							EncryptedFile.create(fis, mirrorPath.toFile(), pk, RSAUtils.getTransformationIdx(),
									AESUtil.getKeyLength(), AESUtil.getTransformationIdx(), cancelled);
							onFile.accept(f, mirrorPath);
						} catch (Exception ex) {
							onError.accept(ex);
						}
					});
				}
			} else {
				// descend
				var mirrorPath = targetDir.resolve(f.getName());

				if (!simulate) {
					Files.createDirectories(mirrorPath);
				}

				for (var child : f.listFiles()) {
					encryptAndMirror(child, mirrorPath, pk, executorService, cancelled, onError, onFile, onDir,
							simulate);

					if (cancelled.get())
						return;
				}

				onDir.accept(f, mirrorPath);
			}
		} catch (Exception e) {
			onError.accept(e);
		}
	}

	private static void onEncryptedFile(File encryptedFile) throws NoSuchAlgorithmException, IOException {

		var keyRequest = new KeyRequest(encryptedFile);

		QRFrame.showForMessage(keyRequest.getMessage(), false, true, null, s -> {
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
							doneDialog.setTitle(Main.APP_NAME);
							doneDialog.setHeaderText(
									String.format("%s successfully decrypted, what's next?", outfile.getName()));

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
							}

							resumeClipboardCheck();
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
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			if (fileType != null)
				fileChooser.getExtensionFilters()
						.add(new ExtensionFilter(fileType.toUpperCase() + " files", "*." + fileType.toLowerCase()));
			var selectedFile = fileChooser.showSaveDialog(FxMain.getPrimaryStage());

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
