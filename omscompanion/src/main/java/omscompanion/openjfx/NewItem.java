package omscompanion.openjfx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.FxMain;
import omscompanion.Main;

public class NewItem {

	@FXML
	private BorderPane bPaneMain;

	@FXML
	private TextArea txtAreaInfo;

	private void init(byte[] unprotected) throws Exception {
		txtAreaInfo.setText(
				"Your data has been encrypted and copied to the clipboard.\r\n\r\nUse the toolbar below to copy the encrypted text again or generate QR codes (these will also be copied to the clipboard).\r\n\r\nThe clipboard will be cleared as soon as you close this window.");

		var encryptionToolBar = new EncryptionToolBar();
		encryptionToolBar.init();
		encryptionToolBar.setUnprotected(unprotected);

		bPaneMain.setBottom(encryptionToolBar);
	}

	public static void showForMessage(byte[] message, Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/NewItem.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((NewItem) fxmlLoader.getController()).init(message);
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": Encrypt Data");
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
				if (andThen != null)
					andThen.run();
			}
		});
	}

}
