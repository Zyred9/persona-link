package com.personalink.server.ai;

import com.personalink.server.service.impl.LocalAssetService;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GeneratedImageStorageTest {
    private final GeneratedImageStorage storage = new GeneratedImageStorage(mock(LocalAssetService.class),
            "aliyuncs.com,volces.com", 10000);

    @Test
    void imageUrlMustStayOnTrustedHttpsHostWithoutRedirectOrCredentials() {
        assertEquals("result.oss-cn-beijing.aliyuncs.com", this.storage.validateUrl(
                "https://result.oss-cn-beijing.aliyuncs.com/image.png?Expires=100").getHost());
        for (String url : new String[]{"http://result.aliyuncs.com/a.png", "https://127.0.0.1/a.png",
                "https://aliyuncs.com.attacker.example/a.png", "https://u:p@result.aliyuncs.com/a.png",
                "https://result.aliyuncs.com:8443/a.png", "file:///C:/secret", "not a url"}) {
            assertThrows(IllegalArgumentException.class, () -> this.storage.validateUrl(url));
        }
    }

    @Test
    void outputHasExactSizeWithoutCroppingOrStretching() throws Exception {
        BufferedImage source = new BufferedImage(220, 100, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 220, 100);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        BufferedImage detail = ImageIO.read(new ByteArrayInputStream(this.storage.normalize(bytes.toByteArray(), 1100, 500)));
        assertEquals(1100, detail.getWidth());
        assertEquals(500, detail.getHeight());
        assertEquals(Color.RED.getRGB(), detail.getRGB(0, 0));
        BufferedImage cover = ImageIO.read(new ByteArrayInputStream(this.storage.normalize(bytes.toByteArray(), 800, 800)));
        assertEquals(800, cover.getWidth());
        assertEquals(800, cover.getHeight());
        assertEquals(Color.WHITE.getRGB(), cover.getRGB(0, 0));
        assertEquals(Color.RED.getRGB(), cover.getRGB(400, 400));
        BufferedImage custom = ImageIO.read(new ByteArrayInputStream(this.storage.normalize(bytes.toByteArray(), 900, 1200)));
        assertEquals(900, custom.getWidth());
        assertEquals(1200, custom.getHeight());
        BufferedImage wide = ImageIO.read(new ByteArrayInputStream(this.storage.normalize(bytes.toByteArray(), 2200, 1000)));
        assertEquals(2200, wide.getWidth());
        assertEquals(1000, wide.getHeight());
        assertEquals(Color.RED.getRGB(), wide.getRGB(0, 0));
        assertThrows(IOException.class, () -> this.storage.normalize(new byte[]{1, 2, 3}, 800, 800));
    }
}
