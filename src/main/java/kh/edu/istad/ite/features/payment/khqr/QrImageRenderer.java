package kh.edu.istad.ite.features.payment.khqr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Component
public class QrImageRenderer {

    private static final int DEFAULT_SIZE = 512;
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";
    private static final Color KHQR_RED = new Color(218, 41, 28);

    public String toPngDataUri(String payload) {
        return toPngDataUri(payload, DEFAULT_SIZE);
    }

    public String toPngDataUri(String payload, int size) {
        return DATA_URI_PREFIX + Base64.getEncoder().encodeToString(toPngBytes(payload, size));
    }

    public byte[] toPngBytes(String payload, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.MARGIN, 2);

            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);

            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int logoWidth = (int) (size * 0.24);
            int logoHeight = (int) (size * 0.20);
            int x = (size - logoWidth) / 2;
            int y = (size - logoHeight) / 2;

            // Outer white background box to isolate logo from QR modules
            g.setColor(Color.WHITE);
            g.fillRoundRect(x - 4, y - 4, logoWidth + 8, logoHeight + 8, 16, 16);

            // Red border
            g.setColor(KHQR_RED);
            g.setStroke(new java.awt.BasicStroke(3.5f));
            g.drawRoundRect(x, y, logoWidth, logoHeight, 12, 12);

            // Draw "KHQR" text in RED
            g.setColor(KHQR_RED);
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (logoHeight * 0.45)));
            FontMetrics fm = g.getFontMetrics();
            int textX = x + (logoWidth - fm.stringWidth("KHQR")) / 2;
            int textY = y + ((logoHeight - fm.getHeight()) / 2) + fm.getAscent();
            g.drawString("KHQR", textX, textY);

            g.dispose();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", outputStream);

            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render QR image", exception);
        }
    }
}
