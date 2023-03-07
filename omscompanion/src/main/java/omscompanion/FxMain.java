package omscompanion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;
import omscompanion.qr.AnimatedQrHelper;

public class FxMain extends Application {

	public static void main(String[] args) {
		Platform.setImplicitExit(false);
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		// TODO Auto-generated method stub

	}

	public static void handleException(Exception ex) {
		ex.printStackTrace();
		Platform.runLater(() -> {
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Oops...");
			alert.setHeaderText(ex.getMessage());
			alert.setContentText(ex.toString());
			alert.showAndWait();
		});
	}

	public static void onGifFile(char[] message) {
		try {
			File f = AnimatedQrHelper.generateGif(message);

			if (f == null)
				return;

			ClipboardUtil.set(f);

			Platform.runLater(() -> {
				Alert alert = new Alert(AlertType.INFORMATION);
				alert.setTitle("Encryption result");
				alert.setHeaderText("Successfully encrypted as .gif file");
				alert.setContentText("Clipboard has been set");
				alert.showAndWait();
			});
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}
	}

	public static void onGifBase64(char[] message) {
		try {
			File f = AnimatedQrHelper.generateGif(message);

			if (f == null)
				return;

			try {
				byte[] bArr = Files.readAllBytes(f.toPath());
				ClipboardUtil.set(Base64.getEncoder().encodeToString(bArr));
			} catch (IOException ex) {
				FxMain.handleException(ex);
				return;
			}

			Platform.runLater(() -> {
				Alert alert = new Alert(AlertType.INFORMATION);
				alert.setTitle("Encryption result");
				alert.setHeaderText("Successfully encrypted as GIF BASE64");
				alert.setContentText("Clipboard has been set");
				alert.showAndWait();
			});
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}

	}

	public static void onAsText(byte[] message) {
		createTextLink(message);

		Platform.runLater(() -> {
			Alert alert = new Alert(AlertType.INFORMATION);
			alert.setTitle("Encryption result");
			alert.setHeaderText("Successfully encrypted as " + MessageComposer.OMS_PREFIX + "...");
			alert.setContentText("Clipboard has been set");
			alert.showAndWait();
		});
	}

	public static void createTextLink(byte[] message) {
		String omsURL = MessageComposer.encodeAsOmsText(message);
		ClipboardUtil.set(omsURL);
	}

}
