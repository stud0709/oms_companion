package omscompanion;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOInvalidTreeException;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

public class AnimatedGifWriter {
	protected final ImageWriter writer;
	protected final ImageWriteParam params;
	protected final IIOMetadata metadata;

	public AnimatedGifWriter(ImageOutputStream out, int imageType, long delay, boolean loop) throws IOException {
		writer = ImageIO.getImageWritersBySuffix("gif").next();
		params = writer.getDefaultWriteParam();

		ImageTypeSpecifier imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
		metadata = writer.getDefaultImageMetadata(imageTypeSpecifier, params);

		createRootMetadata(delay, loop);

		writer.setOutput(out);
		writer.prepareWriteSequence(null);
	}

	private void createRootMetadata(long delay_ms, boolean loop) throws IIOInvalidTreeException {
		var metaFormatName = metadata.getNativeMetadataFormatName();
		var root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

		var graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
		graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
		graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
		graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
		graphicsControlExtensionNode.setAttribute("delayTime", Long.toString(delay_ms / 10));
		graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

		var appExtensionsNode = getNode(root, "ApplicationExtensions");
		var child = new IIOMetadataNode("ApplicationExtension");
		child.setAttribute("applicationID", "NETSCAPE");
		child.setAttribute("authenticationCode", "2.0");

		var loopContinuously = loop ? 0 : 1;
		child.setUserObject(
				new byte[] { 0x1, (byte) (loopContinuously & 0xFF), (byte) ((loopContinuously >> 8) & 0xFF) });
		appExtensionsNode.appendChild(child);
		metadata.setFromTree(metaFormatName, root);
	}

	private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
		var nNodes = rootNode.getLength();
		for (var i = 0; i < nNodes; i++) {
			if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
				return (IIOMetadataNode) rootNode.item(i);
			}
		}
		var node = new IIOMetadataNode(nodeName);
		rootNode.appendChild(node);
		return (node);
	}

	public void writeToSequence(RenderedImage img) throws IOException {
		writer.writeToSequence(new IIOImage(img, null, metadata), params);
	}

	public void close() throws IOException {
		writer.endWriteSequence();
	}

	public static void createGif(List<BitMatrix> list, ImageOutputStream ios, long delay_ms) throws IOException {
		var writer = new AnimatedGifWriter(ios, MatrixToImageWriter.toBufferedImage(list.get(0)).getType(), delay_ms,
				true);
		list.stream().map(m -> MatrixToImageWriter.toBufferedImage(m)).forEach(i -> {
			try {
				writer.writeToSequence(i);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}
}
