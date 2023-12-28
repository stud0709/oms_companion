package omscompanion.openjfx;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javafx.beans.property.SimpleObjectProperty;

public class Profile {
	public ProfileSettings settings;
	public PathAndChecksum[] pathAndChecksum;
	public PathLengthAndDate[] pathLengthAndDate;

	@JsonIgnore
	public SimpleObjectProperty<File> file = new SimpleObjectProperty<>();
	@JsonIgnore
	public Profile backup;

	public Profile(ProfileSettings settings, PathAndChecksum[] pathAndChecksum, PathLengthAndDate[] pathLengthAndDate) {
		this.settings = settings;
		this.pathAndChecksum = pathAndChecksum;
		this.pathLengthAndDate = pathLengthAndDate;
	}

	public Profile() {

	}
}