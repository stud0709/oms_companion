package omscompanion.openjfx;

public class ProfileSettings {
	public String sourceDir, destinationDir, publicKeyName;
	public String[] exclusionList, inclusionList;

	public ProfileSettings(String sourceDir, String destinationDir, String[] exclusionList, String[] inclusionList,
			String publicKeyName) {
		this.sourceDir = sourceDir;
		this.destinationDir = destinationDir;
		this.publicKeyName = publicKeyName;
		this.exclusionList = exclusionList;
		this.inclusionList = inclusionList;
	}

	public ProfileSettings() {
	}
}