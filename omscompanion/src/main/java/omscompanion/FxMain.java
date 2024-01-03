package omscompanion;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Dimension2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import omscompanion.qr.AnimatedQrHelper;

public class FxMain extends Application {
	private static Stage primaryState;

	public static void main(String[] args) {
		Platform.setImplicitExit(false);
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		FxMain.primaryState = primaryStage;
	}

	public static ImageView getImageView(String name, Dimension2D size) {
		var image = new Image(FxMain.class.getResourceAsStream("/omscompanion/img/" + name + ".png"));
		var imageView = new ImageView(image);
		imageView.setFitWidth(size.getWidth());
		imageView.setFitHeight(size.getHeight());

		return imageView;
	}

	public static Image getImage(String name) {
		return new Image(FxMain.class.getResourceAsStream("/omscompanion/img/" + name + ".png"));
	}

	public static Stage getPrimaryStage() {
		return primaryState;
	}

	public static void handleException(Exception ex) {
		ex.printStackTrace();
		Platform.runLater(() -> {
			var alert = new Alert(AlertType.ERROR);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText(ex.getClass().getName());
			alert.setContentText(ex.getMessage());
			alert.showAndWait();
		});
	}

	public static void onGifFile(byte[] message) {
		try {
			var f = AnimatedQrHelper.generateGif(MessageComposer.encodeAsOmsText(message).toCharArray());

			if (f == null)
				return;

			ClipboardUtil.set(f);

			Platform.runLater(() -> {
				var alert = new Alert(AlertType.INFORMATION);
				alert.setTitle("Encryption result");
				alert.setHeaderText("Successfully encrypted as .gif file");
				alert.setContentText("Clipboard has been set");
				alert.showAndWait();
			});
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}
	}

	public static void onGifBase64(byte[] message) {
		try {
			var f = AnimatedQrHelper.generateGif(MessageComposer.encodeAsOmsText(message).toCharArray());

			if (f == null)
				return;

			try {
				var bArr = Files.readAllBytes(f.toPath());
				ClipboardUtil.set(Base64.getEncoder().encodeToString(bArr));
			} catch (IOException ex) {
				FxMain.handleException(ex);
				return;
			}

			Platform.runLater(() -> {
				var alert = new Alert(AlertType.INFORMATION);
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
			var alert = new Alert(AlertType.INFORMATION);
			alert.setTitle("Encryption result");
			alert.setHeaderText("Successfully encrypted as " + MessageComposer.OMS_PREFIX + "...");
			alert.setContentText("Clipboard has been set");
			alert.showAndWait();
		});
	}

	public static void createTextLink(byte[] message) {
		var omsURL = MessageComposer.encodeAsOmsText(message);
		ClipboardUtil.set(omsURL);
	}

}
