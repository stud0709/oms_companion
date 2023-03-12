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
import omscompanion.MessageComposer;

public class NewItem {

	@FXML
	private BorderPane bPaneMain;

	@FXML
	private TextArea txtAreaInfo;

	private void init(byte[] unprotected) throws Exception {
		txtAreaInfo.setText(String.format(
				"We have encrypted your data and copied them in %s... format back to the clipboard.\r\n\r\nUse the tool bar below to generate other formats (they will be copied to the clipboard as well).\r\n\r\nThe clipboard will be cleared as soon as you close this window.",
				MessageComposer.OMS_PREFIX));

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
				stage.setTitle(Main.APP_NAME + ": encrypt data");
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