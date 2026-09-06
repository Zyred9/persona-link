package com.personalink.server.util;

/** 两家生图服务共同支持的最终分辨率边界。 */
public final class ImageResolutionUtil {
    private ImageResolutionUtil() { }

    public static boolean isValid(int width, int height) {
        long pixels = (long) width * height;
        return width >= 256 && height >= 256
                && pixels >= 262144 && pixels <= 4194304
                && width <= 3L * height && height <= 3L * width;
    }
}
