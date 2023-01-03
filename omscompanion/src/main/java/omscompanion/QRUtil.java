package omscompanion;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class QRUtil {
	public static final int CHUNK_SIZE = 200, BARCODE_SIZE = 400;

	public static List<BitMatrix> getQrSequence(String message, int chunkSize, int barcodeSize)
			throws NoSuchAlgorithmException, IOException, WriterException {
		char[] data = message.toCharArray();
		QRCodeWriter writer = new QRCodeWriter();
		List<BitMatrix> list = new ArrayList<>();
		char[] cArr = new char[chunkSize];
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

			String bcs = bc.stream().collect(Collectors.joining("\t"));
			list.add(writer.encode(bcs, BarcodeFormat.QR_CODE, barcodeSize, barcodeSize));

			charsToSend -= chunkSize;
		}

		return list;
	}

}
