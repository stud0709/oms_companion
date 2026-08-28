package omscompanion.openjfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.crypto.PairingInfo;

public class NostrPairing {

	@FXML
	private ChoiceBox<RSAPublicKeyItem> choiceKey;

	@FXML
	private Button btnDefault;

	@FXML
	private Button btnStart;

	@FXML
	private Button btnCancel;

	@FXML
	private Button btnResetRelays;

	@FXML
	private Spinner<Integer> spin_to_connect;

	@FXML
	private TextArea txtRelays;

	private static Stage instance;
	private Stage ownStage;

	private Runnable runOnEndOfAction;

	public void init(Stage stage, Runnable andThen) {
		ownStage = stage;

		runOnEndOfAction = () -> {
			synchronized (NostrPairing.class) {
				if (instance == ownStage) {
					instance = null;

					if (andThen != null)
						new Thread(andThen).start();
				}
			}
		};

		try {
			FxMain.initChoiceBox(choiceKey, Main.getDefaultKeyAlias());
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}

		btnDefault.setOnAction(e -> {
			var selected = choiceKey.getSelectionModel().getSelectedItem();
			if (selected != null) {
				Main.setDefaultKeyAlias(selected.toString());
			}
		});

		spin_to_connect.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(30, 999));
		spin_to_connect.getValueFactory().setValue(PairingInfo.getRequestTimeoutS());
		spin_to_connect.getValueFactory().valueProperty()
				.addListener((observable, oldValue, newValue) -> Main.properties
						.setProperty(PairingInfo.PROP_REQUEST_TIMEOUT_S, Integer.toString(newValue)));

		// Populate relays
		List<String> currentRelays = PairingInfo.getPreferredRelays();
		txtRelays.setText(String.join("\n", currentRelays));

		btnResetRelays.setOnAction(e -> {
			txtRelays.setText(String.join("\n", PairingInfo.DEFAULT_RELAYS));
		});

		btnCancel.setOnAction(e -> {
			ownStage.close();
		});

		// cancellation
		stage.getScene().getWindow().setOnHidden(e -> {
			runOnEndOfAction.run();
		});

		btnStart.setOnAction(e -> {
			var selectedKeyItem = choiceKey.getSelectionModel().getSelectedItem();
			if (selectedKeyItem == null || selectedKeyItem.publicKey == null) {
				Platform.runLater(() -> {
					var alert = new Alert(AlertType.ERROR);
					alert.setTitle(Main.APP_NAME);
					alert.setHeaderText("No Public Key Selected");
					alert.setContentText("Please select a valid RSA public key for pairing.");
					alert.showAndWait();
				});
				return;
			}

			int timeout = spin_to_connect.getValue();
			Main.properties.setProperty(PairingInfo.PROP_REQUEST_TIMEOUT_S, Integer.toString(timeout));

			List<String> relays = parseRelays(txtRelays.getText());
			if (relays.isEmpty()) {
				relays = PairingInfo.DEFAULT_RELAYS;
			}
			PairingInfo.setPreferredRelays(relays);

			Platform.runLater(() -> {
				ownStage.getScene().getWindow().setOnHidden(null);
				ownStage.close();
			});

			try {
				new PairingInfo(timeout, relays, null, null, null, selectedKeyItem.publicKey).displayPrompt(andThen);
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

	private static List<String> parseRelays(String text) {
		if (text == null || text.trim().isEmpty()) {
			return new ArrayList<>();
		}
		return Arrays.stream(text.split("[\r\n,;]+"))
				.map(String::trim)
				.filter(s -> !s.isEmpty() && (s.startsWith("ws://") || s.startsWith("wss://")))
				.collect(Collectors.toList());
	}

	public static void show(Runnable andThen) {
		synchronized (NostrPairing.class) {
			if (instance != null) {
				Platform.runLater(() -> {
					instance.getScene().getWindow().setOnHidden(null);
					instance.close();
				});
			}
		}

		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/NostrPairing.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				var frameController = (NostrPairing) fxmlLoader.getController();
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": Nostr Pairing");
				stage.setScene(scene);
				frameController.init(stage, andThen);
				stage.setResizable(false);
				stage.getIcons().add(FxMain.getImage("qr-code"));
				synchronized (NostrPairing.class) {
					instance = stage;
				}
				stage.show();
			} catch (Exception ex) {
				ex.printStackTrace();
				if (andThen != null)
					new Thread(andThen).start();
			}
		});
	}
}
