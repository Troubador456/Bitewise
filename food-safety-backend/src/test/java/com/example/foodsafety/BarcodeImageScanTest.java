package com.example.foodsafety;

import com.example.foodsafety.service.BarcodeImageService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeImageScanTest {
    private final BarcodeImageService service = new BarcodeImageService();

    @Test
    void decodesProductBarcodeFromUploadedImage() throws Exception {
        var matrix = new MultiFormatWriter().encode(
                "0028400642033", BarcodeFormat.EAN_13, 500, 150);
        var imageBytes = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", imageBytes);
        var image = new MockMultipartFile("file", "barcode.png", "image/png", imageBytes.toByteArray());

        assertEquals("028400642033", service.decode(image));
    }

    @Test
    void rejectsImagesWithoutAReadableBarcode() {
        var image = new MockMultipartFile(
                "file",
                "plain.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47});

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.decode(image));

        assertTrue(exception.getReason().contains("supported image"));
    }
}
