package omscompanion.openjfx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.crypto.RSAUtils;

public class PublicKeyImport {

	@FXML
	private Button btnSave;

	@FXML
	private TextField txtKeyAlias;

	@FXML
	private TextArea txtKeyData;

	private void init() {
		btnSave.setOnAction(e -> {
			try {
				var alias = this.txtKeyAlias.getText().trim();
				if (alias.isEmpty()) {
					throw new Exception("Key Alias may not be empty");
				}

				var bArr = Base64.getDecoder().decode(txtKeyData.getText().trim().replaceAll("[\\r\\n]", ""));
				var publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bArr));

				var p = PublicKeyImport.savePublicKey(alias, publicKey, backupPath -> {
					var alert = new Alert(AlertType.CONFIRMATION);
					alert.setTitle(Main.APP_NAME);
					alert.setTitle("Create backup and overwrite?");
					alert.setContentText("Key '" + alias + "' already exists.");
					var opt = alert.showAndWait();
					return opt.filter(t -> t == ButtonType.OK).isPresent();
				});

				if (p == null)
					return;

				var alert = new Alert(AlertType.INFORMATION);
				alert.setTitle("omsCompanion");
				alert.setHeaderText("Import was successful");
				alert.showAndWait();

				btnSave.getScene().getWindow().hide();

			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});

		txtKeyData.requestFocus();
	}

	public static Path savePublicKey(String alias, PublicKey rsaPublicKey, Function<Path, Boolean> onOverwrite)
			throws Exception {
		var fn = alias + "." + rsaPublicKey.getFormat().toLowerCase();
		var publicKeyPath = Main.PUBLIC_KEY_STORAGE.resolve(fn);

		if (Files.exists(publicKeyPath)) {
			// create backup of the old key
			var pk = RSAUtils.getPublicKey(Files.readAllBytes(publicKeyPath));

			var fingerprint = RSAUtils.getFingerprint(pk);

			var fn_backup = fn + "." + Main.byteArrayToHex(fingerprint).replaceAll("\\W", "") + ".bak";

			var backupPath = Main.PUBLIC_KEY_STORAGE.resolve(fn_backup);

			if (!onOverwrite.apply(backupPath))
				return null;

			Files.copy(publicKeyPath, backupPath);

		}

		Files.write(publicKeyPath, rsaPublicKey.getEncoded());
		return publicKeyPath;
	}

	public static void show(Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/PublicKeyImport.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((PublicKeyImport) fxmlLoader.getController()).init();
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": import public key");
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
				scene.getWindow().setOnHidden(e -> {
					if (andThen != null)
						andThen.run();
				});
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

}
