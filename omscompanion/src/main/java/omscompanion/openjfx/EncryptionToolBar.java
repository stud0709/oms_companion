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
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.crypto.AESUtil;
import omscompanion.crypto.EncryptedMessageTransfer;
import omscompanion.crypto.RSAUtils;

public class EncryptionToolBar extends GridPane implements ChangeListener<String> {
	public static final String FILE_TYPE_PUCLIC_KEY = ".x.509";

	private String message;
	byte[] unprotected;

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
	ChoiceBox<String> choiceKey;

	@FXML
	private Label lblKey;

	public EncryptionToolBar() throws IOException {
		var url = Main.class.getResource("openjfx/EncryptionToolBar.fxml");
		var fxmlLoader = new FXMLLoader(url);
		fxmlLoader.setRoot(this);
		fxmlLoader.setController(this);
		fxmlLoader.load();
	}

	public void setUnprotected(byte[] unprotected) {
		this.unprotected = unprotected;
		this.changed(null, null, choiceKey.getSelectionModel().getSelectedItem());
		FxMain.createTextLink(message.getBytes());
	}

	public void init() throws Exception {
		List<String> publicKeys = Files.list(Main.PUBLIC_KEY_STORAGE).map(p -> p.getFileName().toString())
				.filter(fn -> fn.toLowerCase().endsWith(FILE_TYPE_PUCLIC_KEY)).collect(Collectors.toList());

		if (publicKeys.isEmpty()) {
			throw new Exception(
					String.format("Cannot encrypt: no keys found in %s", Main.PUBLIC_KEY_STORAGE.toAbsolutePath()));
		}

		ObservableList<String> os = FXCollections.observableArrayList(publicKeys);
		choiceKey.setItems(os);

		String defaultKey = Main.getDefaultKey();

		if (publicKeys.size() == 1 || defaultKey == null || !publicKeys.contains(defaultKey)) {
			choiceKey.getSelectionModel().selectFirst();
		} else {
			choiceKey.getSelectionModel().select(defaultKey);
		}

		choiceKey.getSelectionModel().selectedItemProperty().addListener(this);
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
			QRFrame.showForMessage(message, () -> Platform.runLater(() -> btnPreviewQr.setDisable(false)));
			break;
		}
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

}
