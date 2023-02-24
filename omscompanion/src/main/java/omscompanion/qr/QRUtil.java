package omscompanion.qr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import omscompanion.Main;

public class QRUtil {
	private static final int CHUNK_SIZE = 200, BARCODE_SIZE = 400;
	private static final String PROP_CHUNK_SIZE = "barcode_chunk_size", PROP_BARCODE_SIZE = "barcode_size";

	/**
	 * Cuts a message into chunks and creates a barcode for every chunk. Every
	 * barcode contains (as readable text, separated by TAB):
	 * <ul>
	 * <li>transaction ID, same for all QR codes in the sequence</li>
	 * <li>chunk number</li>
	 * <li>total number of chunks</li>
	 * <li>data length in this chunk (padding is added to the last code)</li>
	 * <li>data</li>
	 * </ul>
	 * 
	 * @throws WriterException
	 */
	public static List<BitMatrix> getQrSequence(String message, int chunkSize, int barcodeSize) throws WriterException {
		char[] data = message.toCharArray();
		QRCodeWriter writer = new QRCodeWriter();
		List<BitMatrix> list = new ArrayList<>();
		char[] cArr;
		int chunks = (int) Math.ceil(data.length / (double) chunkSize);
		int charsToSend = data.length;
		String transactionId = Integer.toHexString((int) (Math.random() * 0xffff));

		for (int chunkNo = 0; chunkNo < chunks; chunkNo++) {
			// copy with padding to keep all barcodes equal in size
			cArr = Arrays.copyOfRange(data, chunkSize * chunkNo, chunkSize * (chunkNo + 1));

			List<String> bc = new ArrayList<>();
			bc.add(transactionId);
			bc.add(Integer.toString(chunkNo));
			bc.add(Integer.toString(chunks));
			bc.add(Integer.toString(Math.min(chunkSize, charsToSend)));
			bc.add(new String(cArr));

			String bcs = String.join("\t", bc);
			list.add(writer.encode(bcs, BarcodeFormat.QR_CODE, barcodeSize, barcodeSize));

			charsToSend -= chunkSize;
		}

		return list;
	}

	public static int getChunkSize() {
		return Integer.parseInt(Main.properties.getProperty(PROP_CHUNK_SIZE, "" + CHUNK_SIZE));
	}

	public static int getBarcodeSize() {
		return Integer.parseInt(Main.properties.getProperty(PROP_BARCODE_SIZE, "" + BARCODE_SIZE));
	}

}
