package com.cropcoders.procurement.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

class QrCodeTest {

    @Test
    void thePictureReadsBackAsTheSameText() throws Exception {
        byte[] png = QrCode.png("SFPS:42:TNJ-017", 360);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(360);
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)));

        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo("SFPS:42:TNJ-017");
    }
}
