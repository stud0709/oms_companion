package omscompanion.openjfx;

import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
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
import omscompanion.crypto.EncryptedMessage;
import omscompanion.crypto.RSAUtils;

public class EncryptionToolBar extends GridPane implements ChangeListener<RSAPublicKeyItem> {
	public static final String FILE_TYPE_PUCLIC_KEY = ".x.509";

	private byte[] message;
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
	ChoiceBox<RSAPublicKeyItem> choiceKey;

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
		FxMain.createTextLink(message);
	}

	public static void initChoiceBox(ChoiceBox<RSAPublicKeyItem> choiceBox) throws Exception {
		var publicKeys = Files.list(Main.PUBLIC_KEY_STORAGE)
				.filter(fn -> fn.getFileName().toString().toLowerCase().endsWith(FILE_TYPE_PUCLIC_KEY))
				.map(p -> new RSAPublicKeyItem(p))
				.filter(p -> p.publicKey != null /* this happens if the key could not be restored */)
				.collect(Collectors.toList());

		if (publicKeys.isEmpty()) {
			throw new Exception(
					String.format("Cannot encrypt: no keys found in %s", Main.PUBLIC_KEY_STORAGE.toAbsolutePath()));
		}

		var os = FXCollections.observableArrayList(publicKeys);
		choiceBox.setItems(os);

		var defaultKey = os.stream().filter(i -> i.toString().equals(Main.getDefaultKey())).findAny();

		if (publicKeys.size() == 1 || !defaultKey.isPresent()) {
			choiceBox.getSelectionModel().selectFirst();
		} else {
			choiceBox.getSelectionModel().select(defaultKey.get());
		}
	}

	public void init() throws Exception {
		initChoiceBox(choiceKey);
		choiceKey.getSelectionModel().selectedItemProperty().addListener(this);
	}

	@FXML
	void onBtnAction(ActionEvent event) {
		switch (((Node) event.getSource()).getId()) {
		case "btnAsGifBase64":
			FxMain.onGifBase64(message);
			break;
		case "btnAsGifFile":
			FxMain.onGifFile(message);
			break;
		case "btnAsText":
			FxMain.onAsText(message);
			break;
		case "btnDefault":
			Main.setDefaultKeyAlias(choiceKey.getSelectionModel().getSelectedItem().toString());
			break;
		case "btnPreviewQr":
			btnPreviewQr.setDisable(true);
			QRFrame.showForMessage(message, false, false, s -> Platform.runLater(() -> btnPreviewQr.setDisable(false)));
			break;
		}
	}

	@Override
	public void changed(ObservableValue<? extends RSAPublicKeyItem> observable, RSAPublicKeyItem oldValue,
			RSAPublicKeyItem newValue) {
		try {
			message = new EncryptedMessage(unprotected, newValue.publicKey, RSAUtils.getRsaTransformationIdx(),
					AESUtil.getKeyLength(), AESUtil.getTransformationIdx()).getMessage();

		} catch (Exception ex) {
			FxMain.handleException(ex);
		}
	}

}
