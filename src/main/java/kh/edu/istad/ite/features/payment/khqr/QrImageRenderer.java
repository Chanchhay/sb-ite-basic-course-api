package kh.edu.istad.ite.features.payment.khqr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Component
public class QrImageRenderer {

    private static final int DEFAULT_SIZE = 512;
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    public String toPngDataUri(String payload) {
        return toPngDataUri(payload, DEFAULT_SIZE);
    }

    public String toPngDataUri(String payload, int size) {
        return DATA_URI_PREFIX + Base64.getEncoder().encodeToString(toPngBytes(payload, size));
    }

    public byte[] toPngBytes(String payload, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.US_ASCII.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render QR image", exception);
        }
    }
}
