package omscompanion.openjfx;

public class ProfileSettings {
	public String sourceDir, destinationDir, publicKeyName;
	public String[] exclusionList, inclusionList;
	boolean checksum;

	public ProfileSettings(String sourceDir, String destinationDir, String[] exclusionList, String[] inclusionList,
			String publicKeyName, boolean checksum) {
		this.sourceDir = sourceDir;
		this.destinationDir = destinationDir;
		this.publicKeyName = publicKeyName;
		this.exclusionList = exclusionList;
		this.inclusionList = inclusionList;
		this.checksum = checksum;
	}

	public ProfileSettings() {
	}
}