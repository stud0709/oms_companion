package omscompanion.openjfx;

import java.io.File;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javafx.beans.property.SimpleObjectProperty;

public class Profile {
	public ProfileSettings settings;
	public PathAndChecksum[] pathAndChecksum;

	@JsonIgnore
	public SimpleObjectProperty<File> file = new SimpleObjectProperty<>();
	@JsonIgnore
	public Profile backup;

	public Profile(ProfileSettings settings, PathAndChecksum[] pathAndChecksum) {
		this.settings = settings;
		this.pathAndChecksum = pathAndChecksum;
	}

	public Profile() {

	}
}