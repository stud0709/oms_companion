package omscompanion.openjfx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.MessageComposer;
import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedMessageTransfer;
import omscompanion.crypto.RSAUtils;

public class NewItemController implements ChangeListener<String> {
	public static final String FILE_TYPE_PUCLIC_KEY = ".x.509";

	@FXML
	private Button btnAsGifBase64;

	@FXML
	private Button btnAsGifFile;

	@FXML
	private Button btnAsText;

	@FXML
	private Button btnDefault;

	@FXML
	private Button btnPreviewQr;

	@FXML
	private ChoiceBox<String> choiceKey;

	@FXML
	private Label lblKey;

	@FXML
	private TextArea txtAreaInfo;

	private String message;
	private byte[] unprotected;

	public void init(byte[] bArr) throws IOException {
		unprotected = bArr;

		txtAreaInfo.setText(String.format(
				"We have encrypted your data and copied them in %s... format back to the clipboard.\r\n\r\nUse the tool bar below to generate other formats (they will be copied to the clipboard as well).\r\n\r\nThe clipboard will be cleared as soon as you close this window.",
				MessageComposer.OMS_PREFIX));

		List<String> publicKeys = Files.list(Main.PUBLIC_KEY_STORAGE).map(p -> p.getFileName().toString())
				.filter(fn -> fn.toLowerCase().endsWith(FILE_TYPE_PUCLIC_KEY)).collect(Collectors.toList());

		ObservableList<String> os = FXCollections.observableArrayList(publicKeys);
		choiceKey.setItems(os);

		String defaultKey = Main.getDefaultKey();

		if (publicKeys.size() == 1 || defaultKey == null || !publicKeys.contains(defaultKey)) {
			choiceKey.getSelectionModel().selectFirst();
		} else {
			choiceKey.getSelectionModel().select(defaultKey);
		}

		choiceKey.getSelectionModel().selectedItemProperty().addListener(this);

		this.changed(null, null, choiceKey.getSelectionModel().getSelectedItem());
	}

	@Override
	public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
		try {
			Path pkPath = Main.PUBLIC_KEY_STORAGE.resolve(newValue);

			RSAPublicKey pk = Main.getPublicKey(Files.readAllBytes(pkPath));

			message = new EncryptedMessageTransfer(unprotected, pk, RSAUtils.getRsaTransformationIdx(),
					AESUtil.getKeyLength(), AESUtil.getAesTransformationIdx()).getMessage();
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}
	}

	@FXML
	void onBtnAction(ActionEvent event) {
		switch (((Node) event.getSource()).getId()) {
		case "btnAsGifBase64":
			FxMain.onGifBase64(message.toCharArray());
			break;
		case "btnAsGifFile":
			FxMain.onGifFile(message.toCharArray());
			break;
		case "btnAsText":
			FxMain.onAsText(message.getBytes());
			break;
		case "btnDefault":
			Main.setDefaultKeyAlias(choiceKey.getSelectionModel().getSelectedItem());
			break;
		case "btnPreviewQr":
			btnPreviewQr.setDisable(true);
			QRFrameController.showForMessage(message, () -> Platform.runLater(() -> btnPreviewQr.setDisable(false)));
			break;
		}
	}

	public static void showForMessage(byte[] message, Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/NewItem.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((NewItemController) fxmlLoader.getController()).init(message);
				var stage = new Stage();
				stage.setTitle("omsCompanion");
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					if (andThen != null)
						andThen.run();
				});
			} catch (Exception ex) {
				ex.printStackTrace();
				if (andThen != null)
					andThen.run();
			}
		});
	}

}