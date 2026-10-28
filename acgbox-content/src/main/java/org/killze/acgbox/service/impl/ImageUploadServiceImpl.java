package org.killze.acgbox.service.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.config.CosStorageProperties;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.service.ImageUploadService;
import org.killze.acgbox.vo.content.ImageUploadVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;

/**
 * 校验 JPEG、PNG 原始内容后上传 COS，不转码、不落业务文件、不写数据库。
 */
@Service
@Slf4j
public class ImageUploadServiceImpl implements ImageUploadService {
    private static final int MAX_SIZE = 5 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");

    @Autowired
    private CosStorageProperties properties;
    @Autowired
    private ObjectProvider<COSClient> cosClientProvider;

    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    /**
     * 所有格式、尺寸和地址检查均在 COS PUT 之前完成。
     */
    @Override
    public ImageUploadVO uploadImage(MultipartFile file, String targetType) {
        if (!properties.isEnabled()) {
            throw new BusinessException("图片上传功能未启用");
        }
        LocalDate receivedDate = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        String directory = switch (targetType == null ? "" : targetType) {
            case "ANIME" -> "anime";
            case "BOOK" -> "book";
            default -> throw new BusinessException("targetType 必须为 ANIME 或 BOOK");
        };
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择非空图片文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new MaxUploadSizeExceededException(MAX_SIZE);
        }
        String name = file.getOriginalFilename();
        String suffix = name == null || name.lastIndexOf('.') < 0 ? ""
                : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!suffix.equals("jpg") && !suffix.equals("jpeg") && !suffix.equals("png")) {
            throw new BusinessException("仅支持 JPG、JPEG、PNG 图片");
        }
        byte[] bytes;
        try (var stream = file.getInputStream()) {
            bytes = stream.readNBytes(MAX_SIZE + 1);
        } catch (IOException e) {
            throw new BusinessException("图片文件读取失败，请重新选择");
        }
        if (bytes.length > MAX_SIZE) {
            throw new MaxUploadSizeExceededException(MAX_SIZE);
        }
        ImageInfo info = inspectImage(bytes, suffix);
        String objectKey = directory + "/" + receivedDate.format(MONTH_FORMAT) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + info.extension();
        String url = properties.getPublicBaseUrl() + "/" + objectKey;
        if (url.length() > 500) {
            throw new BusinessException("图片访问地址超过 500 个字符，请检查存储配置");
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType(info.contentType());
        metadata.setCacheControl("public, max-age=31536000, immutable");
        try (var stream = new ByteArrayInputStream(bytes)) {
            var result = cosClientProvider.getObject().putObject(
                    new PutObjectRequest(properties.getBucket(), objectKey, stream, metadata));
            if (result == null || result.getETag() == null || result.getETag().isBlank()) {
                throw new BusinessException("图片上传失败，请稍后重试");
            }
        } catch (CosServiceException e) {
            // 不记录 SDK 异常正文、请求地址、凭证或签名，只保留定位所需的状态码。
            log.warn("COS 图片上传失败，状态码：{}", e.getStatusCode());
            throw new BusinessException("图片上传失败，请稍后重试");
        } catch (CosClientException | IOException e) {
            log.warn("COS 图片上传失败，异常类型：{}", e.getClass().getSimpleName());
            throw new BusinessException("图片上传失败，请稍后重试");
        }
        return ImageUploadVO.builder().url(url).objectKey(objectKey).targetType(targetType)
                .contentType(info.contentType()).size((long) bytes.length)
                .width((long) info.width()).height((long) info.height()).build();
    }

    /**
     * 根据文件特征选择解码器，先读取尺寸，再完整解码；不信任客户端 MIME 类型。
     */
    private ImageInfo inspectImage(byte[] bytes, String suffix) {
        boolean png = bytes.length >= 8 && Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(bytes, 8));
        boolean jpeg = bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255 && (bytes[bytes.length - 2] & 255) == 255
                && (bytes[bytes.length - 1] & 255) == 217;
        if ((!png && !jpeg) || png != suffix.equals("png")) {
            throw new BusinessException("图片内容无效或与文件扩展名不一致");
        }
        if (png) {
            validatePngChunks(bytes);
        }
        var readers = ImageIO.getImageReadersByFormatName(png ? "PNG" : "JPEG");
        if (!readers.hasNext()) {
            throw new BusinessException("图片格式暂时无法读取");
        }
        ImageReader reader = readers.next();
        boolean[] warning = {false};
        reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0 || width > 10000 || height > 10000
                    || (long) width * height > 20000000L) {
                throw new BusinessException("图片单边不能超过 10000 像素且总像素不能超过 20000000");
            }
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new BusinessException("图片内容损坏，无法完整解码");
            }
            image.flush();
            if (warning[0]) {
                throw new BusinessException("图片内容损坏，无法完整解码");
            }
            return new ImageInfo(png ? "png" : "jpg", png ? "image/png" : "image/jpeg", width, height);
        } catch (IOException | IllegalArgumentException e) {
            throw new BusinessException("图片内容损坏，无法完整解码");
        } finally {
            reader.dispose();
        }
    }

    /**
     * PNG 解码器可能忽略 CRC 或缺失的结束块，额外检查块边界、校验和及完整结束标记。
     */
    private void validatePngChunks(byte[] bytes) {
        int offset = 8;
        while (offset <= bytes.length - 12) {
            long length = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, offset, 4).getInt());
            if (length > bytes.length - offset - 12L) {
                break;
            }
            int size = (int) length;
            CRC32 crc = new CRC32();
            crc.update(bytes, offset + 4, size + 4);
            long expected = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, offset + 8 + size, 4).getInt());
            if (crc.getValue() != expected) {
                break;
            }
            boolean end = bytes[offset + 4] == 'I' && bytes[offset + 5] == 'E'
                    && bytes[offset + 6] == 'N' && bytes[offset + 7] == 'D';
            offset += size + 12;
            if (end) {
                if (size == 0 && offset == bytes.length) {
                    return;
                }
                break;
            }
        }
        throw new BusinessException("图片内容损坏，无法完整解码");
    }

    private record ImageInfo(String extension, String contentType, int width, int height) {
    }
}
