package com.personalink.server.ai;

import com.personalink.server.service.impl.LocalAssetService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import com.personalink.server.util.ImageResolutionUtil;

/** 仅下载可信生图结果，限制资源消耗并转存为指定尺寸的自有 PNG。 */
@Component
public class GeneratedImageStorage {
    private static final int MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024;
    private static final long MAX_PIXELS = 24_000_000;
    private final LocalAssetService assets;
    private final List<String> hostSuffixes;
    private final int timeoutMillis;

    public GeneratedImageStorage(LocalAssetService assets,
            @Value("${app.image-generation.download-host-suffixes:aliyuncs.com,volces.com,volccdn.com,volcengineapi.com}") String hosts,
            @Value("${app.image-generation.read-timeout-millis:30000}") int timeoutMillis) {
        this.assets = assets;
        this.hostSuffixes = Arrays.stream(hosts.split(",")).map(String::trim)
                .filter(host -> !host.isBlank()).map(host -> host.toLowerCase(Locale.ROOT)).toList();
        this.timeoutMillis = timeoutMillis;
    }

    /** 将上游短期 URL 转存到 OSS；不得接收运营人员提供的任意下载地址。 */
    public String store(String url, int width, int height) {
        if (!ImageResolutionUtil.isValid(width, height)) throw new IllegalArgumentException("生图分辨率超出支持范围");
        URI uri = this.validateUrl(url);
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()
                        || (address.getAddress().length == 16 && (address.getAddress()[0] & 0xfe) == 0xfc)) {
                    throw new IllegalArgumentException("生图结果地址不能指向内网");
                }
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(this.timeoutMillis);
            connection.setReadTimeout(this.timeoutMillis);
            connection.setRequestProperty("Accept", "image/png,image/jpeg");
            long deadline = System.nanoTime() + Duration.ofMillis(this.timeoutMillis).toNanos();
            try {
                if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("生图结果下载失败或文件过大");
                }
                try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (bytes.size() + count > MAX_DOWNLOAD_BYTES || System.nanoTime() > deadline) {
                            throw new IOException("生图结果超过下载限制");
                        }
                        bytes.write(buffer, 0, count);
                    }
                    return this.assets.saveGeneratedImage(this.normalize(bytes.toByteArray(), width, height)).url();
                }
            } finally {
                connection.disconnect();
            }
        } catch (IOException exception) {
            // 不传播可能包含临时签名 URL 的底层消息。
            throw new IllegalStateException("生图结果下载或转存失败，请重试保存结果", exception);
        }
    }

    URI validateUrl(String url) {
        URI uri;
        try { uri = URI.create(Objects.requireNonNull(url)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("生图结果地址不合法"); }
        String host = Objects.isNull(uri.getHost()) ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(uri.getScheme()) || Objects.nonNull(uri.getUserInfo())
                || (uri.getPort() != -1 && uri.getPort() != 443) || Objects.nonNull(uri.getFragment())
                || this.hostSuffixes.stream().noneMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix))) {
            throw new IllegalArgumentException("生图结果必须是可信图片域名的 HTTPS 地址");
        }
        return uri;
    }

    byte[] normalize(byte[] bytes, int targetWidth, int targetHeight) throws IOException {
        if (!ImageResolutionUtil.isValid(targetWidth, targetHeight)) throw new IllegalArgumentException("生图分辨率超出支持范围");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("生图结果不是支持的图片格式");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("生图结果像素超过限制");
                }
                BufferedImage original = reader.read(0);
                BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = result.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, targetWidth, targetHeight);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    double scale = Math.min((double) targetWidth / width, (double) targetHeight / height);
                    int scaledWidth = Math.max(1, (int) Math.round(width * scale));
                    int scaledHeight = Math.max(1, (int) Math.round(height * scale));
                    graphics.drawImage(original, (targetWidth - scaledWidth) / 2, (targetHeight - scaledHeight) / 2,
                            scaledWidth, scaledHeight, null);
                } finally { graphics.dispose(); }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(result, "png", output);
                return output.toByteArray();
            } finally { reader.dispose(); }
        }
    }
}
