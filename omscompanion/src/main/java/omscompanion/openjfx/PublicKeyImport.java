package omscompanion.openjfx;

import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;

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
import omscompanion.NewPrivateKey;

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
				String alias = this.txtKeyAlias.getText().trim();
				if (alias.isEmpty()) {
					throw new Exception("Key Alias may not be empty");
				}

				byte[] bArr = Base64.getDecoder().decode(txtKeyData.getText().trim().replaceAll("[\\r\\n]", ""));
				PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bArr));

				Path p = NewPrivateKey.savePublicKey(alias, publicKey, backupPath -> {
					Alert alert = new Alert(AlertType.CONFIRMATION);
					alert.setTitle(Main.APP_NAME);
					alert.setTitle("Create backup and overwrite?");
					alert.setContentText("Key '" + alias + "' already exists.");
					Optional<ButtonType> opt = alert.showAndWait();
					return opt.filter(t -> t == ButtonType.OK).isPresent();
				});

				if (p == null)
					return;

				Alert alert = new Alert(AlertType.INFORMATION);
				alert.setTitle("omsCompanion");
				alert.setHeaderText("Import was successful");
				alert.showAndWait();

				btnSave.getScene().getWindow().hide();

			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

	public static void show() {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/PublicKeyImport.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((PublicKeyImport) fxmlLoader.getController()).init();
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME);
				stage.setScene(scene);
				stage.initStyle(StageStyle.UTILITY);
				stage.show();
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

}
