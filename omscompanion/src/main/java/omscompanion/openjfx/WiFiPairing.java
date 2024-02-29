package omscompanion.openjfx;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.crypto.PairingInfo;

public class WiFiPairing {

	@FXML
	private Button btnStart;

	@FXML
	private ChoiceBox<RSAPublicKeyItem> choice_initialKey;

	@FXML
	private PasswordField pwd_requestId;

	@FXML
	private Spinner<Integer> spin_to_connect;

	private static Stage instance;
	private Stage ownStage;

	private Runnable runOnEndOfAction;

	public void init(Stage stage, Runnable andThen) throws Exception {
		ownStage = stage;

		runOnEndOfAction = () -> {
			synchronized (WiFiPairing.class) {
				if (instance == ownStage) {
					instance = null;

					if (andThen != null)
						new Thread(andThen).start();
				}
			}
		};

		spin_to_connect.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999));
		spin_to_connect.getValueFactory().setValue(PairingInfo.getRequestTimeoutS());
		spin_to_connect.getValueFactory().valueProperty()
				.addListener((observable, oldValue, newValue) -> Main.properties
						.setProperty(PairingInfo.PROP_REQUEST_TIMEOUT_S, Integer.toString(newValue)));

		// cancellation
		stage.getScene().getWindow().setOnHidden(e -> {
			runOnEndOfAction.run();
		});

		choice_initialKey.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> updateUI());
		pwd_requestId.textProperty().addListener((observable, oldValue, newValue) -> updateUI());

		btnStart.setDisable(true);

		FxMain.initChoiceBox(choice_initialKey, PairingInfo.getDefaultInitialKeyAlias());

		btnStart.setOnAction(e -> {
			Main.properties.setProperty(PairingInfo.PROP_REQUEST_TIMEOUT_S,
					Integer.toString(spin_to_connect.getValue()));
			Main.properties.setProperty(PairingInfo.PROP_PAIRING_DEF_INIT_KEY,
					choice_initialKey.getSelectionModel().getSelectedItem().toString());

			Platform.runLater(() -> {
				ownStage.getScene().getWindow().setOnHidden(null);
				ownStage.close();
			});

			try {
				new PairingInfo().displayPrompt(pwd_requestId.getText(), new PairingInfo.ConnectionSettings()
						.withInitialKey(choice_initialKey.getSelectionModel().getSelectedItem().publicKey), andThen);
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});

		pwd_requestId.requestFocus();
	}

	private void updateUI() {
		boolean disabled = choice_initialKey.getSelectionModel().getSelectedItem() == null
				|| pwd_requestId.getText().isEmpty();
		btnStart.setDisable(disabled);
	}

	public static void show(Runnable andThen) {
		synchronized (WiFiPairing.class) {
			if (instance != null) {
				Platform.runLater(() -> {
					instance.getScene().getWindow().setOnHidden(null);
					instance.close();
				});
			}
		}

		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/WiFiPairing.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				var frameController = (WiFiPairing) fxmlLoader.getController();
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": WiFi Pairing");
				stage.setScene(scene);
				frameController.init(stage, andThen);
				stage.setResizable(false);
				stage.getIcons().add(FxMain.getImage("qr-code"));
				synchronized (WiFiPairing.class) {
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
