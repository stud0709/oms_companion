package omscompanion.openjfx;

public class PathLengthAndDate {
	public final String path;
	public final long length, lastModified;

	public PathLengthAndDate(String path, long length, long lastModified) {
		this.path = path;
		this.length = length;
		this.lastModified = lastModified;
	}
}
