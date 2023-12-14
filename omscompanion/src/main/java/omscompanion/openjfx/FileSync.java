package omscompanion.openjfx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;

public class FileSync {
	private static final String TYPE_PROFILE = ".profile", PROP_LAST_PROFILE = "filesync_last_profile",
			NEW_PROFILE = "(new profile)", PLEASE_SELECT = "(please select)";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleObjectProperty<File> sourceDir = new SimpleObjectProperty<>(),
			destinationDir = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<Profile> currentProfile = new SimpleObjectProperty<>();

	private static final int STATE_INITIAL = 0, STATE_READY_TO_ANALYZE = 1, STATE_ANALYZING = 2,
			STATE_READY_TO_SYNC = 3, STATE_SYNCING = 4;
	private final SimpleIntegerProperty state = new SimpleIntegerProperty(-1);

	public record Profile(String sourceDir, String destinationDir, String[] exclusionList, String[] inclusionList,
			String publicKeyName, boolean checksum) {

	}

	@FXML
	private Button btn_analyze;

	@FXML
	private Button btn_del_exclude;

	@FXML
	private Button btn_del_include;

	@FXML
	private Button btn_del_profile;

	@FXML
	private Button btn_destdir;

	@FXML
	private Button btn_save;

	@FXML
	private Button btn_save_as;

	@FXML
	private Button btn_srcdir;

	@FXML
	private Button btn_start_sync;

	@FXML
	private Button btn_stop;

	@FXML
	private CheckBox chk_checksum;

	@FXML
	private ChoiceBox<String> choice_profile;

	@FXML
	private ChoiceBox<String> choice_public_key;

	@FXML
	private Label lbl_srcdir;

	@FXML
	private Label lbl_targetdir;

	@FXML
	private ListView<String> list_exclude;

	@FXML
	private ListView<String> list_include;

	@FXML
	private ListView<String> list_source;

	@FXML
	private ProgressIndicator progress_sync;

	private void init(Runnable andThen) throws Exception {
		btn_analyze.setText(null);
		setIcon("analyze", btn_analyze);
		btn_analyze.setTooltip(new Tooltip("Analyze"));

		btn_del_exclude.setText(null);
		setIcon("trash_icon", btn_del_exclude);
		btn_del_exclude.setTooltip(new Tooltip("Delete Exclusion Rule"));

		btn_del_include.setText(null);
		setIcon("trash_icon", btn_del_include);
		btn_del_include.setTooltip(new Tooltip("Delete Inclusion Rule"));

		btn_del_profile.setText(null);
		setIcon("trash_icon", btn_del_profile);
		btn_del_profile.setTooltip(new Tooltip("Delete Profile"));

		btn_save.setText(null);
		setIcon("save_icon", btn_save);
		btn_save.setTooltip(new Tooltip("Save"));

		btn_save_as.setText(null);
		setIcon("save_as_icon", btn_save_as);
		btn_save_as.setTooltip(new Tooltip("Save As..."));

		btn_srcdir.setText(null);
		btn_srcdir.setTooltip(new Tooltip("Select Source Directory..."));
		setIcon("horizontal_dots", btn_srcdir);

		btn_destdir.setText(null);
		btn_destdir.setTooltip(new Tooltip("Select Destination Directory..."));
		setIcon("horizontal_dots", btn_destdir);

		btn_start_sync.setText(null);
		btn_start_sync.setTooltip(new Tooltip("Start Sync"));
		setIcon("logout_icon", btn_start_sync);

		btn_stop.setText(null);
		btn_stop.setTooltip(new Tooltip("Stop Sync"));
		setIcon("x_icon", btn_stop);

		EncryptionToolBar.initChoiceBox(choice_public_key);

		btn_del_exclude.setDisable(true);
		btn_del_include.setDisable(true);
		btn_save.setDisable(true);
		btn_save_as.setDisable(true);
		btn_analyze.setDisable(true);
		btn_start_sync.setDisable(true);
		btn_stop.setDisable(true);

		list_exclude.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_exclude.setDisable(list_exclude.getItems().isEmpty());
				updateUI(state.get());
			}
		});

		list_exclude.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> {
					btn_del_exclude.setDisable(list_exclude.getSelectionModel().getSelectedItems().isEmpty());
				});

		list_exclude.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		list_include.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_include.setDisable(list_include.getItems().isEmpty());
				updateUI(state.get());
			}
		});

		list_include.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> {
					btn_del_include.setDisable(list_include.getSelectionModel().getSelectedItems().isEmpty());
				});

		list_include.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		state.addListener((observable, oldValue, newValue) -> updateUI(newValue));
		chk_checksum.selectedProperty().addListener((observable, oldValue, newValue) -> updateUI(state.get()));

		sourceDir.addListener((observable, oldValue, newValue) -> {
			lbl_srcdir.setText(newValue == null ? PLEASE_SELECT : newValue.getAbsolutePath().toString());

			list_exclude.getItems().clear();
			list_include.getItems().clear();
			list_source.getItems().clear();

			state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
		});

		lbl_srcdir.setText(PLEASE_SELECT);

		destinationDir.addListener((observable, oldValue, newValue) -> {
			lbl_targetdir.setText(newValue == null ? PLEASE_SELECT : newValue.getAbsolutePath().toString());

			state.set(isDirConfigValid() ? STATE_READY_TO_ANALYZE : STATE_INITIAL);
		});

		lbl_targetdir.setText(PLEASE_SELECT);

		currentProfile.addListener((observable, oldValue, newValue) -> onCurrentProfileChange(newValue));

		loadProfiles();
	}

	private void updateUI(Number newValue) {
		Platform.runLater(() -> {
			btn_start_sync
					.setDisable(state.get() != STATE_READY_TO_SYNC || choice_public_key.getSelectionModel().isEmpty());
			btn_stop.setDisable(state.get() != STATE_ANALYZING && state.get() != STATE_SYNCING);
			chk_checksum.setDisable(state.get() == STATE_ANALYZING);
			btn_destdir.setDisable(state.get() == STATE_ANALYZING || state.get() == STATE_SYNCING);
			btn_srcdir.setDisable(state.get() == STATE_ANALYZING || state.get() == STATE_SYNCING);
			btn_analyze.setDisable(state.get() != STATE_READY_TO_ANALYZE && state.get() != STATE_READY_TO_SYNC);
			btn_save.setDisable(
					!Arrays.asList(new Integer[] { STATE_READY_TO_ANALYZE, STATE_READY_TO_SYNC }).contains(state.get())
							|| choice_profile.getSelectionModel().getSelectedIndex() == 0 || !isProfileChanged());
			btn_save_as.setDisable(!Arrays.asList(new Integer[] { STATE_READY_TO_ANALYZE, STATE_READY_TO_SYNC })
					.contains(state.get()));
		});
	}

	protected boolean isProfileChanged() {
		try {
			if (currentProfile.get() != null) {
				String currentJson = objectMapper.writeValueAsString(currentProfile.get());
				String newJson = objectMapper.writeValueAsString(toProfile());

				return currentJson.equals(newJson);
			}
		} catch (JsonProcessingException ex) {
			ex.printStackTrace();
		}

		return false;
	}

	private Profile toProfile() {
		return new Profile(sourceDir.get() == null ? null : sourceDir.get().getAbsolutePath().toString(),
				destinationDir.get() == null ? null : destinationDir.get().getAbsoluteFile().toString(),
				list_exclude.getItems().toArray(new String[] {}), list_include.getItems().toArray(new String[] {}),
				choice_public_key.getSelectionModel().isEmpty() ? null
						: choice_public_key.getSelectionModel().getSelectedItem(),
				chk_checksum.isSelected());
	}

	private void setIcon(String iconName, Labeled control) {
		var image = new Image(getClass().getResourceAsStream("/omscompanion/img/" + iconName + ".png"));
		var imageView = new ImageView(image);
		imageView.setFitWidth(16);
		imageView.setFitHeight(16);

		control.setGraphic(imageView);
	}

	public static void show(Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/FileSync.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((FileSync) fxmlLoader.getController()).init(andThen);
				var stage = new Stage();
				stage.setTitle(Main.APP_NAME + ": FileSync");
				stage.setScene(scene);
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

	private void loadProfiles() throws IOException {
		var profileList = Files.list(Main.FILESYNC).filter(p -> p.getFileName().toString().endsWith(TYPE_PROFILE))
				.map(p -> p.getFileName().toString()).map(fn -> fn.substring(0, fn.length() - TYPE_PROFILE.length()))
				.sorted().collect(Collectors.toList());
		profileList.add(0, NEW_PROFILE);

		choice_profile.setItems(FXCollections.observableArrayList(profileList));

		choice_profile.getSelectionModel().selectedIndexProperty()
				.addListener((observable, oldValue, newValue) -> onChoiceProfileSelected(newValue.intValue()));

		var lastProfile = (String) Main.properties.get(PROP_LAST_PROFILE);

		if (lastProfile != null) {
			choice_profile.getSelectionModel().select(lastProfile);
		} else {
			choice_profile.getSelectionModel().selectFirst();
		}
	}

	private void onChoiceProfileSelected(int selectedIdx) {
		if (selectedIdx == 0) {
			currentProfile.set(null);
		} else {
			try {
				File f = Main.FILESYNC
						.resolve(choice_profile.getSelectionModel().selectedItemProperty().get() + TYPE_PROFILE)
						.toFile();
				currentProfile.set(objectMapper.readValue(f, Profile.class));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean isDirConfigValid() {
		var s = sourceDir.get();
		var d = destinationDir.get();

		return
		// at least source and target directories should be set
		s != null && d != null
		// source and target must be different
				&& !s.equals(d)
				// and not a subfolder of the other one
				&& !s.toPath().startsWith(d.toPath()) && !d.toPath().startsWith(s.toPath());
	}

	private void onCurrentProfileChange(Profile profile) {
		list_exclude.getItems().clear();
		list_include.getItems().clear();
		list_source.getItems().clear();

		if (profile == null) {
			sourceDir.set(null);
			destinationDir.set(null);
			chk_checksum.setSelected(false);

			state.set(STATE_INITIAL);
			return;
		}

		sourceDir.set(new File(profile.sourceDir));
		destinationDir.set(new File(profile.destinationDir));

		list_exclude.getItems().addAll(profile.exclusionList);
		list_include.getItems().addAll(profile.inclusionList);
		chk_checksum.setSelected(profile.checksum);

		state.set(STATE_READY_TO_ANALYZE);
	}

	@FXML
	void actn_analyze(ActionEvent event) {
		state.set(STATE_ANALYZING);

		// ...

		state.set(STATE_READY_TO_SYNC);
	}

	@FXML
	void actn_del_exclude(ActionEvent event) {

	}

	@FXML
	void actn_del_include(ActionEvent event) {

	}

	@FXML
	void actn_del_profile(ActionEvent event) {

	}

	@FXML
	void actn_save(ActionEvent event) {
		File f = Main.FILESYNC.resolve(choice_profile.getSelectionModel().selectedItemProperty().get() + TYPE_PROFILE)
				.toFile();
		try {
			objectMapper.writeValue(f, currentProfile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	void actn_save_as(ActionEvent event) {

	}

	@FXML
	void actn_select_dest(ActionEvent event) {
		try {
			Platform.runLater(() -> {
				var dirChooser = new DirectoryChooser();
				dirChooser.setTitle("FileSync Destination Directory");
				dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
				var result = dirChooser.showDialog(FxMain.getPrimaryStage());
				if (result == null) {
					return;
				}

				destinationDir.set(result);
			});

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@FXML
	void actn_select_src(ActionEvent event) {
		try {
			Platform.runLater(() -> {
				var dirChooser = new DirectoryChooser();
				dirChooser.setTitle("FileSync Source Directory");
				dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
				var result = dirChooser.showDialog(FxMain.getPrimaryStage());
				if (result == null) {
					return;
				}

				sourceDir.set(result);
			});
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@FXML
	void actn_start_sync(ActionEvent event) {
		btn_stop.setDisable(false);
		btn_analyze.setDisable(true);

		// ...

		btn_stop.setDisable(false);
	}

	@FXML
	void actn_stop(ActionEvent event) {
		switch (state.get()) {
		case STATE_ANALYZING -> state.set(STATE_READY_TO_ANALYZE);
		case STATE_SYNCING -> state.set(STATE_READY_TO_SYNC);
		}
	}
}
