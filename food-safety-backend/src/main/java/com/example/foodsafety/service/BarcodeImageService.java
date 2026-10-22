package com.example.foodsafety.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;

@Service
public class BarcodeImageService {
    private static final long MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Map<DecodeHintType, Object> DECODE_HINTS = Map.of(
            DecodeHintType.TRY_HARDER, Boolean.TRUE,
            DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128,
                    BarcodeFormat.CODE_39,
                    BarcodeFormat.ITF,
                    BarcodeFormat.QR_CODE
            )
    );

    public String decode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty barcode image is required");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 10 MB or smaller");
        }

        final BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read uploaded image", exception);
        }
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is not a supported image");
        }

        try {
            Result result = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))),
                    DECODE_HINTS);
            if (!result.getText().matches("\\d{8,14}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Image barcode must contain an 8 to 14 digit product code");
            }
            return result.getText();
        } catch (NotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No supported barcode could be read. Try a clear, well-lit image with the full barcode visible.",
                    exception);
        }
    }
}
