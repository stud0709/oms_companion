package omscompanion.openjfx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.crypto.PairingInfo;

public class NostrPairing {

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
				new PairingInfo(timeout, relays).displayPrompt(andThen);
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
