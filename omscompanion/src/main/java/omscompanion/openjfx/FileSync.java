package omscompanion.openjfx;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import omscompanion.FxMain;
import omscompanion.Main;
import omscompanion.MessageComposer;

public class FileSync {
	private static final String TYPE_PROFILE = ".omsfs", NEW_PROFILE = "(new profile)",
			PLEASE_SELECT = "(please select)";
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SimpleObjectProperty<Path> sourceDir = new SimpleObjectProperty<>(),
			destinationDir = new SimpleObjectProperty<>();
	private final SimpleObjectProperty<Profile> currentProfileProperty = new SimpleObjectProperty<>();

	private enum State {
		INITIAL, READY_TO_ANALYZE, ANALYZING, READY_TO_SYNC, SYNCING;
	}

	private enum Result {
		ERROR, UNCHANGED, DELETED, COPIED;
	}

	private static final int TOTAL_SIZE_UNKNOWN = -1;

	private final SimpleObjectProperty<State> stateProperty = new SimpleObjectProperty<>(State.INITIAL);
	private final ObservableList<SyncEntry> tableItems = FXCollections.observableArrayList();
	private final FilteredList<SyncEntry> filteredList = new FilteredList<>(tableItems);
	private final SimpleLongProperty totalSizeProperty = new SimpleLongProperty();

	private class SyncEntry {
		public final SimpleObjectProperty<Path> pathProperty = new SimpleObjectProperty<>();
		public final long length;
		public final SimpleObjectProperty<CompareResult> compareProperty = new SimpleObjectProperty<>();

		public SyncEntry(Path path) {
			pathProperty.set(path);
			var _length = 0L;
			try {
				_length = Files.isDirectory(path) ? 0 : Files.size(path);
			} catch (IOException e) {
				e.printStackTrace();
				compareProperty.set(new CompareResult(e));
			}

			length = _length;
		}
	}

	public static record CompareResult(Result result, Exception exception) {
		public CompareResult(Exception e) {
			this(Result.ERROR, e);
		}

		public CompareResult(Result compare) {
			this(compare, null);
		}
	};

	private Profile newProfile() {
		var profile = new Profile(new ProfileSettings(null, null, new String[] {}, new String[] {},
				choice_public_key.getValue(), chk_checksum.isSelected()), new PathAndChecksum[] {},
				new PathLengthAndDate[] {});
		profile.backup = new Profile(new ProfileSettings(null, null, new String[] {}, new String[] {},
				choice_public_key.getValue(), chk_checksum.isSelected()), new PathAndChecksum[] {},
				new PathLengthAndDate[] {});
		return profile;
	}

	private AtomicBoolean loading = new AtomicBoolean();

	// icons by https://www.iconfinder.com/search?q=&iconset=heroicons-solid

	@FXML
	private Button btn_add_exclude;

	@FXML
	private Button btn_add_include;

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
	private Button btn_new;

	@FXML
	private Button btn_open;

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
	private ChoiceBox<String> choice_public_key;

	@FXML
	private TableColumn<SyncEntry, Path> col_filename;

	@FXML
	private TableColumn<SyncEntry, CompareResult> col_sync_result;

	@FXML
	private TableColumn<SyncEntry, Path> col_flag_folder;

	@FXML
	private TableColumn<SyncEntry, Path> col_level;

	@FXML
	private Label lbl_srcdir;

	@FXML
	private Label lbl_profile;

	@FXML
	private Label lbl_targetdir;

	@FXML
	private Label lbl_total;

	@FXML
	private ListView<String> list_exclude;

	@FXML
	private ListView<String> list_include;

	@FXML
	private TableView<SyncEntry> tbl_source;

	private void init(Scene scene) throws Exception {
		Platform.runLater(() -> {
			scene.getWindow().setOnCloseRequest(event -> {
				if (isProfileChanged() && isDirConfigValid()) {
					var alert = new Alert(AlertType.WARNING);
					alert.setTitle(Main.APP_NAME);
					alert.setHeaderText("Unsaved Changes Detected");
					alert.setContentText("Close FileSync without saving changes?");
					alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
					var result = alert.showAndWait();
					result.ifPresent(bt -> {
						if (bt == ButtonType.NO) // do not close window
							event.consume();
					});
				}
			});
		});

		btn_analyze.setText(null);
		btn_analyze.setGraphic(getIcon("analyze"));
		btn_analyze.setTooltip(new Tooltip("Analyze"));

		btn_del_exclude.setText(null);
		btn_del_exclude.setGraphic(getIcon("trash_icon"));
		btn_del_exclude.setTooltip(new Tooltip("Delete Exclusion Rule"));

		btn_del_include.setText(null);
		btn_del_include.setGraphic(getIcon("trash_icon"));
		btn_del_include.setTooltip(new Tooltip("Delete Inclusion Rule"));

		btn_del_profile.setText(null);
		btn_del_profile.setGraphic(getIcon("trash_icon"));
		btn_del_profile.setTooltip(new Tooltip("Delete Profile"));

		btn_save.setText(null);
		btn_save.setGraphic(getIcon("save_icon"));
		btn_save.setTooltip(new Tooltip("Save"));

		btn_save_as.setText(null);
		btn_save_as.setGraphic(getIcon("save_as_icon"));
		btn_save_as.setTooltip(new Tooltip("Save As..."));

		btn_srcdir.setText(null);
		btn_srcdir.setTooltip(new Tooltip("Select Source Directory..."));
		btn_srcdir.setGraphic(getIcon("horizontal_dots"));

		btn_destdir.setText(null);
		btn_destdir.setTooltip(new Tooltip("Select Destination Directory..."));
		btn_destdir.setGraphic(getIcon("horizontal_dots"));

		btn_start_sync.setText(null);
		btn_start_sync.setTooltip(new Tooltip("Start Sync"));
		btn_start_sync.setGraphic(getIcon("logout_icon"));

		btn_stop.setText(null);
		btn_stop.setTooltip(new Tooltip("Stop Sync"));
		btn_stop.setGraphic(getIcon("x_icon"));

		btn_open.setText(null);
		btn_open.setTooltip(new Tooltip("Open Profile"));
		btn_open.setGraphic(getIcon("horizontal_dots"));

		btn_new.setText(null);
		btn_new.setTooltip(new Tooltip("New Profile"));
		btn_new.setGraphic(getIcon("plus_circle_icon"));

		btn_add_exclude.setGraphic(getIcon("plus_circle_icon"));
		btn_add_exclude.setText(null);

		btn_add_include.setGraphic(getIcon("plus_circle_icon"));
		btn_add_include.setText(null);

		EncryptionToolBar.initChoiceBox(choice_public_key);

		btn_del_exclude.setDisable(true);
		btn_del_include.setDisable(true);
		btn_save.setDisable(true);
		btn_save_as.setDisable(true);
		btn_analyze.setDisable(true);
		btn_start_sync.setDisable(true);
		btn_stop.setDisable(true);
		btn_add_exclude.setDisable(true);
		btn_add_include.setDisable(true);

		lbl_total.setGraphic(getIcon("database_icon"));
		lbl_total.setTooltip(new Tooltip("Total Data"));

		filteredList.predicateProperty().addListener((observable, oldValue, newValue) -> calculateTotalSize());

		setupTable();

		list_exclude.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_exclude.setDisable(list_exclude.getItems().isEmpty());
				if (!loading.get())
					currentProfileProperty.get().settings.exclusionList = list_exclude.getItems()
							.toArray(new String[] {});
				updateUI();
			}
		});

		totalSizeProperty.addListener((observable, oldValue, newValue) -> onTotalSizeChanged(newValue));
		totalSizeProperty.set(TOTAL_SIZE_UNKNOWN);

		list_exclude.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> updateUI());

		list_exclude.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		list_include.getItems().addListener(new ListChangeListener<String>() {

			@Override
			public void onChanged(Change<? extends String> c) {
				btn_del_include.setDisable(list_include.getItems().isEmpty());
				if (!loading.get())
					currentProfileProperty.get().settings.inclusionList = list_include.getItems()
							.toArray(new String[] {});
				updateUI();
			}
		});

		list_include.getSelectionModel().getSelectedItems()
				.addListener((ListChangeListener.Change<? extends String> c) -> updateUI());

		list_include.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		stateProperty.addListener((observable, oldValue, newValue) -> updateUI());

		chk_checksum.selectedProperty().addListener((observable, oldValue, newValue) -> {
			if (!loading.get()) {
				currentProfileProperty.get().settings.checksum = newValue;
				stateProperty.set(isDirConfigValid() ? State.READY_TO_ANALYZE : State.INITIAL);
			}
			updateUI();
		});

		sourceDir.addListener((observable, oldValue, newValue) -> {
			lbl_srcdir.setText(newValue == null ? PLEASE_SELECT : newValue.toFile().getAbsolutePath());
			if (!loading.get())
				currentProfileProperty.get().settings.sourceDir = newValue.toFile().getAbsolutePath();

			list_exclude.getItems().clear();
			list_include.getItems().clear();

			stateProperty.set(isDirConfigValid() ? State.READY_TO_ANALYZE : State.INITIAL);
		});

		lbl_srcdir.setText(PLEASE_SELECT);

		destinationDir.addListener((observable, oldValue, newValue) -> {
			lbl_targetdir.setText(newValue == null ? PLEASE_SELECT : newValue.toFile().getAbsolutePath());
			if (!loading.get())
				currentProfileProperty.get().settings.destinationDir = newValue.toFile().getAbsolutePath();

			stateProperty.set(isDirConfigValid() ? State.READY_TO_ANALYZE : State.INITIAL);
		});

		lbl_targetdir.setText(PLEASE_SELECT);

		currentProfileProperty.addListener((observable, oldValue, newValue) -> loadProfile(newValue));

		lbl_profile.setText(NEW_PROFILE);

		currentProfileProperty.set(newProfile());
	}

	private void calculateTotalSize() {
		totalSizeProperty
				.set(filteredList.stream().collect(Collectors.summarizingLong(syncEntry -> syncEntry.length)).getSum());
	}

	private void onTotalSizeChanged(Number newValue) {
		Platform.runLater(() -> {
			if (newValue.longValue() == TOTAL_SIZE_UNKNOWN) {
				lbl_total.setText("unknown");
			} else {
				var size = newValue.doubleValue();
				var uom = "B";

				if (size > 1024) {
					uom = "KB";
					size /= 1024D;
				}

				if (size > 1024) {
					uom = "MB";
					size /= 1024D;
				}

				if (size > 1024) {
					uom = "GB";
					size /= 1024D;
				}

				lbl_total.setText(String.format("%.3f %s", size, uom));
			}
		});
	}

	private void setupTable() {
		tbl_source.setItems(filteredList);
		tbl_source.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		col_filename.prefWidthProperty().bind(tbl_source.widthProperty().subtract(col_sync_result.widthProperty())
				.subtract(col_flag_folder.widthProperty()).subtract(col_level.widthProperty()));
		col_filename.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().pathProperty);
		col_filename.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (empty) {
					setText(null);
					setTooltip(null);
				} else {
					setText(item.getFileName().toString());
					setTooltip(new Tooltip("..." + File.separatorChar + getSubpathOfSource(item)));
				}
			};
		});

		col_flag_folder.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().pathProperty);
		col_flag_folder.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (!empty && Files.isDirectory(item)) {
					this.setGraphic(getIcon("folder_icon"));
				} else {
					setGraphic(null);
				}
			};
		});

		col_flag_folder.setGraphic(getIcon("folder_icon"));

		col_level.setCellValueFactory(cellDataFeatures -> cellDataFeatures.getValue().pathProperty);
		col_level.setCellFactory(col -> new TableCell<SyncEntry, Path>() {
			@Override
			protected void updateItem(Path item, boolean empty) {
				if (empty) {
					setText(null);
				} else {
					setText(Integer.toString(item.getNameCount() - sourceDir.get().getNameCount()));
				}
			};
		});

		col_sync_result.setCellValueFactory(param -> param.getValue().compareProperty);
		col_sync_result.setCellFactory(col -> new TableCell<SyncEntry, CompareResult>() {
			@Override
			protected void updateItem(CompareResult compareResult, boolean empty) {
				if (empty) {
					setGraphic(null);
					setTooltip(null);
				} else {
					if (compareResult != null) {
						switch (compareResult.result) {
						case ERROR -> {
							this.setGraphic(getIcon("lightning_bolt_icon"));
							var ex = compareResult.exception;
							this.setTooltip(
									new Tooltip(ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()));
						}
						case DELETED -> {
							this.setGraphic(getIcon("x_icon"));
							setTooltip(new Tooltip("Deleted"));
						}
						case COPIED -> {
							this.setGraphic(getIcon("logout_icon"));
							setTooltip(new Tooltip("Mirrored to the destination folder"));
						}
						case UNCHANGED -> {
							this.setGraphic(getIcon("check_icon"));
							setTooltip(new Tooltip("Unchanged"));
						}
						}

					} else {
						setGraphic(null);
						setTooltip(null);
					}
				}
			}
		});

		var lvl_label = new Label();
		lvl_label.setGraphic(getIcon("down_double_icon"));
		lvl_label.setTooltip(new Tooltip("Drilldown Level"));

		col_level.setGraphic(lvl_label);

		var result_label = new Label();
		result_label.setGraphic(getIcon("view_grid_icon"));
		result_label.setTooltip(new Tooltip("Sync Result"));

		col_sync_result.setGraphic(result_label);

		tbl_source.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			btn_add_exclude.setDisable(newValue == null);
			btn_add_include.setDisable(newValue == null);
		});
	}

	private void updateUI() {
		Platform.runLater(() -> {
			btn_start_sync.setDisable(
					stateProperty.get() != State.READY_TO_SYNC || choice_public_key.getSelectionModel().isEmpty());

			btn_stop.setDisable(
					!Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(stateProperty.get()));

			for (var node : new Node[] { chk_checksum, btn_destdir, btn_srcdir, btn_open, choice_public_key,
					btn_del_include, btn_del_exclude, btn_new }) {
				node.setDisable(
						Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(stateProperty.get()));
			}

			btn_del_profile.setDisable(
					Arrays.asList(new State[] { State.ANALYZING, State.SYNCING }).contains(stateProperty.get())
							|| currentProfileProperty.get().file.get() == null);

			btn_analyze.setDisable(!Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.READY_TO_SYNC })
					.contains(stateProperty.get()));

			btn_save.setDisable(!Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.READY_TO_SYNC })
					.contains(stateProperty.get()) || currentProfileProperty.get().file.get() == null
					|| !isProfileChanged());

			btn_save_as.setDisable(!Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.READY_TO_SYNC })
					.contains(stateProperty.get()));

			btn_del_exclude.setDisable(list_exclude.getSelectionModel().getSelectedItems().isEmpty()
					|| stateProperty.get() != State.READY_TO_SYNC);

			btn_del_include.setDisable(list_include.getSelectionModel().getSelectedItems().isEmpty()
					|| stateProperty.get() != State.READY_TO_SYNC);

			if (Arrays.asList(new State[] { State.READY_TO_ANALYZE, State.INITIAL }).contains(stateProperty.get())) {
				tableItems.clear();
				totalSizeProperty.set(TOTAL_SIZE_UNKNOWN);
			}
		});
	}

	protected boolean isProfileChanged() {
		try {
			String backup = objectMapper.writeValueAsString(currentProfileProperty.get().backup.settings);
			String current = objectMapper.writeValueAsString(currentProfileProperty.get().settings);

			return !backup.equals(current);
		} catch (JsonProcessingException ex) {
			ex.printStackTrace();
		}

		return false;
	}

	private ImageView getIcon(String iconName) {
		var image = new Image(getClass().getResourceAsStream("/omscompanion/img/" + iconName + ".png"));
		var imageView = new ImageView(image);
		imageView.setFitWidth(16);
		imageView.setFitHeight(16);

		return imageView;
	}

	public static void show(Runnable andThen) {
		Platform.runLater(() -> {
			try {
				var url = Main.class.getResource("openjfx/FileSync.fxml");
				var fxmlLoader = new FXMLLoader(url);
				var scene = new Scene(fxmlLoader.load());
				((FileSync) fxmlLoader.getController()).init(scene);
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

	private boolean isDirConfigValid() {
		var s = sourceDir.get();
		var d = destinationDir.get();

		if (s == null || d == null)
			return false;

		if (s.equals(d)) {
			var alert = new Alert(AlertType.ERROR);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("Invalid Directories");
			alert.setContentText("Source and Destination directories may not be the same");
			alert.showAndWait();
			return false;
		}

		if (s.startsWith(d) || d.startsWith(s)) {
			var alert = new Alert(AlertType.ERROR);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("Invalid Directories");
			alert.setContentText("Source and Destination directories may not subfolders of each other");
			alert.showAndWait();
			return false;
		}

		return true;
	}

	/**
	 * Load profile settings into UI
	 * 
	 * @param profile
	 */
	private void loadProfile(Profile profile) {
		loading.set(true);

		try {
			profile.file.addListener((observable, oldValue, newValue) -> onProfileFileChanged(newValue));
			onProfileFileChanged(profile.file.get());
			lbl_profile.setText(
					profile.file.get() == null ? NEW_PROFILE : profile.file.get().getAbsolutePath().toString());
			sourceDir.set(profile.settings.sourceDir == null ? null : Path.of(profile.settings.sourceDir));
			destinationDir
					.set(profile.settings.destinationDir == null ? null : Path.of(profile.settings.destinationDir));
			list_exclude.getItems().setAll(profile.settings.exclusionList);
			list_include.getItems().setAll(profile.settings.inclusionList);
			chk_checksum.setSelected(profile.settings.checksum);
		} finally {
			loading.set(false);
		}
	}

	@FXML
	void actn_analyze(ActionEvent event) {
		stateProperty.set(State.ANALYZING);

		new Thread(() -> analyze()).start();
	}

	private void analyze() {
		try {
			Files.walk(sourceDir.get()).anyMatch(p -> analyzeSingle(p));
			calculateTotalSize();
			stateProperty.set(State.READY_TO_SYNC);
		} catch (Exception ex) {
			FxMain.handleException(ex);
		}
	}

	private boolean analyzeSingle(Path p) {
		if (stateProperty.get() != State.ANALYZING)
			return true; // stop processing

		var level = p.getNameCount() - sourceDir.get().getNameCount();

		if (level == 0)
			return false; // parent folder

		var labelText = new Label(String.format("[%s] ...", level) + File.separatorChar + p.getFileName());
		labelText.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);

		tableItems.add(new SyncEntry(p));

		return false; // continue processing
	}

	@FXML
	void actn_del_exclude(ActionEvent event) {
		list_exclude.getItems().remove(list_exclude.getSelectionModel().getSelectedItem());
		filteredList.setPredicate(e -> matches(e));
	}

	@FXML
	void actn_del_include(ActionEvent event) {
		list_include.getItems().remove(list_include.getSelectionModel().getSelectedItem());
		filteredList.setPredicate(e -> matches(e));
	}

	@FXML
	void actn_del_profile(ActionEvent event) {
		var alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle(Main.APP_NAME);
		alert.setHeaderText("Delete Profile");
		alert.setContentText("Delete " + currentProfileProperty.get().file.get().getName() + "?");
		alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
		alert.showAndWait().ifPresent(buttonType -> {
			if (buttonType == ButtonType.YES)
				try {
					Files.delete(currentProfileProperty.get().file.get().toPath());
					currentProfileProperty.set(newProfile());
				} catch (IOException e) {
					FxMain.handleException(e);
				}
		});
	}

	@FXML
	void actn_save(ActionEvent event) {
		try {
			objectMapper.writeValue(currentProfileProperty.get().file.get(), currentProfileProperty);
			currentProfileProperty.get().backup = objectMapper.readValue(currentProfileProperty.get().file.get(),
					Profile.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	void actn_save_as(ActionEvent event) {
		Platform.runLater(() -> {
			var fileChooser = new FileChooser();
			fileChooser.setTitle("Save Profile As...");
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			fileChooser.getExtensionFilters().setAll(new ExtensionFilter("FileSync Profile", "*" + TYPE_PROFILE));
			var file = fileChooser.showSaveDialog(FxMain.getPrimaryStage());
			if (file == null) {
				return;
			}

			try {
				objectMapper.writeValue(file, currentProfileProperty.get());
				currentProfileProperty.get().file.set(file);
				currentProfileProperty.get().backup = objectMapper.readValue(file, Profile.class);
			} catch (Exception ex) {
				FxMain.handleException(ex);
			}
		});
	}

	@FXML
	void actn_select_dest(ActionEvent event) {
		Platform.runLater(() -> {
			var dirChooser = new DirectoryChooser();
			dirChooser.setTitle("FileSync Destination Directory");
			dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			var result = dirChooser.showDialog(FxMain.getPrimaryStage());
			if (result == null) {
				return;
			}

			destinationDir.set(result.toPath());
		});
	}

	@FXML
	void actn_select_src(ActionEvent event) {
		Platform.runLater(() -> {
			var dirChooser = new DirectoryChooser();
			dirChooser.setTitle("FileSync Source Directory");
			dirChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			var result = dirChooser.showDialog(FxMain.getPrimaryStage());
			if (result == null) {
				return;
			}

			sourceDir.set(result.toPath());
		});
	}

	@FXML
	void actn_start_sync(ActionEvent event) {
		stateProperty.set(State.SYNCING);

		new Thread(() -> {
			// as the files are encrypted, we can only compare against the data in the
			// profile.
			var pathChecksum = Arrays.stream(currentProfileProperty.get().pathAndChecksum)
					.collect(Collectors.toMap(pac -> pac.path, pac -> pac.checksum));
			var checksumPath = Arrays.stream(currentProfileProperty.get().pathAndChecksum)
					.collect(Collectors.toMap(pac -> pac.checksum, pac -> pac.path));
			var pathLengthDate = Arrays.stream(currentProfileProperty.get().pathAndChecksum)
					.collect(Collectors.toMap(pld -> pld.path, pld -> pld));

			// Part 1: left to right
			tbl_source.getItems().stream().forEach(item -> {
				try {
					var keyPath = getSubpathOfSource(item.pathProperty.get());

					if (Files.isDirectory(item.pathProperty.get())) {
						var targetPath = destinationDir.get().resolve(keyPath);

						if (Files.exists(targetPath)) {
							item.compareProperty.set(new CompareResult(Result.UNCHANGED));
						} else {
							Files.createDirectories(targetPath); // create if not exists
							item.compareProperty.set(new CompareResult(Result.COPIED));
						}
					} else {
						var encryptedFileName = String.format("%s.%s", keyPath.getFileName().toString(),
								MessageComposer.OMS_FILE_TYPE);
						var targetPath = keyPath.getNameCount() == 1 ? destinationDir.get().resolve(encryptedFileName)
								: destinationDir.get().resolve(keyPath.getParent()).resolve(encryptedFileName);

						// lookup reference data
						boolean processFile = false;

						if (chk_checksum.isSelected()) {
							// use checksum
							String checksumRef = pathChecksum.get(keyPath);
							if (checksumRef == null) {
								// new file
								processFile = true;
							} else {

								// TODO: calculate checksum, compare
							}
						} else {
							// use file date and size
							var pld = pathLengthDate.get(keyPath);

							if (pld == null) {
								processFile = true;
							} else {
								// TODO: compare length and date
							}
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					item.compareProperty.set(new CompareResult(e));
				}
			});

			stateProperty.set(State.READY_TO_SYNC);
		}).start();

	}

	@FXML
	void actn_stop(ActionEvent event) {
		switch (stateProperty.get()) {
		case ANALYZING -> stateProperty.set(State.READY_TO_ANALYZE);
		case SYNCING -> stateProperty.set(State.READY_TO_SYNC);
		default -> throw new IllegalStateException();
		}
	}

	@FXML
	void actn_btn_new(ActionEvent event) {
		if (isProfileChanged()) {
			var alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle(Main.APP_NAME);
			alert.setHeaderText("New Profile");
			alert.setContentText("Discard current changes" + (currentProfileProperty.get().file.get() == null ? ""
					: " of " + currentProfileProperty.get().file.get().getName()) + "?");

			alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
			alert.showAndWait().ifPresent(buttonType -> {
				if (buttonType == ButtonType.YES)
					currentProfileProperty.set(newProfile());
			});
			return;
		}

		currentProfileProperty.set(newProfile());
	}

	@FXML
	void actn_open_profile(ActionEvent event) throws StreamReadException, DatabindException, IOException {
		Platform.runLater(() -> {
			var fileChooser = new FileChooser();
			fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
			fileChooser.setTitle("Open Profile");
			fileChooser.getExtensionFilters().add(new ExtensionFilter("FileSync Profile", "*" + TYPE_PROFILE));

			var selectedFile = fileChooser.showOpenDialog(FxMain.getPrimaryStage());

			if (selectedFile == null)
				return;

			lbl_profile.setText(selectedFile.getAbsolutePath());
			try {
				var profile = objectMapper.readValue(selectedFile, Profile.class);
				profile.backup = objectMapper.readValue(selectedFile, Profile.class);
				profile.file.addListener((observable, oldValue, newValue) -> onProfileFileChanged(newValue));

				profile.file.set(selectedFile);
				currentProfileProperty.set(profile);
			} catch (Exception ex) {
				currentProfileProperty.set(null);
				FxMain.handleException(ex);
			}
		});
	}

	private void onProfileFileChanged(File f) {
		btn_del_profile.setDisable(f == null);
		lbl_profile.setText(f == null ? NEW_PROFILE : f.getAbsolutePath().toString());
	}

	@FXML
	private void actn_add_to_list(ActionEvent event) {
		var syncEntry = tbl_source.getSelectionModel().selectedItemProperty().get();

		var textInputDialog = new TextInputDialog(getSubpathOfSource(syncEntry.pathProperty.get()).toString());

		textInputDialog.setTitle(Main.APP_NAME);
		textInputDialog.setHeaderText(String.format(
				"Create %s Rule. Asterix (*) and question mark (?) may be used as wildcard. INCLUDE rules will be processed first.",
				event.getSource() == btn_add_include ? "INCLUDE" : "EXCLUDE"));
		textInputDialog.setContentText("..." + File.separatorChar);

		var rule = textInputDialog.showAndWait();

		if (!rule.isPresent())
			return;

		if (event.getSource() == btn_add_exclude) {
			list_exclude.getItems().add(rule.get());
		} else {
			list_include.getItems().add(rule.get());
		}

		filteredList.setPredicate(e -> matches(e));
	}

	private Path getSubpathOfSource(Path item) {
		return item.subpath(sourceDir.get().getNameCount(), item.getNameCount());
	}

	private boolean matches(SyncEntry syncEntry) {
		var subpath = getSubpathOfSource(syncEntry.pathProperty.get()).toString();

		if (!list_include.getItems().isEmpty()) {
			// check for include
			if (!list_include.getItems().stream().map(s -> toPattern(s)).anyMatch(s -> subpath.matches(s)))
				return false;
		}

		return !list_exclude.getItems().stream().map(s -> toPattern(s)).anyMatch(s -> subpath.matches(s));
	}

	private String toPattern(String s) {
		var pat = Pattern.compile("([^\\*\\?]*)([\\*\\?])?");
		var m = pat.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			if (m.group(1) != null)
				sb.append(Pattern.quote(m.group(1)));

			if (m.group(2) != null)
				switch (m.group(2)) {
				case "*" -> sb.append(".*");
				case "?" -> sb.append(".");
				}
		}
		return sb.toString();
	}
}
