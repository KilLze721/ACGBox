package org.killze.acgbox.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.service.EmailVerificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 邮箱验证码服务实现类，Redis 只保存带密钥的验证码摘要，不保存明文验证码。
 */
@Service
@Slf4j
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private static final RedisScript<Long> RESERVE_SCRIPT = loadScript("redis/account-email/reserve.lua");
    private static final RedisScript<Long> VERIFY_SCRIPT = loadScript("redis/account-email/verify.lua");
    private static final RedisScript<Long> REMOVE_SCRIPT = loadScript("redis/account-email/remove.lua");
    private static final RedisScript<Long> SAVE_CHANGE_TOKEN_SCRIPT = loadScript("redis/account-email/save-change-token.lua");
    private static final RedisScript<Long> VERIFY_CHANGE_TOKEN_SCRIPT = loadScript("redis/account-email/verify-change-token.lua");

    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Autowired
    private SecretKey secretKey;

    @Value("${account-email.enabled:false}")
    private boolean enabled;

    @Value("${account-email.from:}")
    private String from;

    /**
     * 先原子检查发送额度并保存摘要，再发送验证码邮件。
     */
    @Override
    public void sendCode(Long id, Long tokenVersion, String purpose, String email) {
        checkEnabled();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || !StringUtils.hasText(from)) {
            throw new BusinessException("邮件服务尚未配置");
        }
        String operation = switch (purpose) {
            case "BIND" -> "绑定或验证此邮箱";
            case "CHANGE" -> "确认更换账号邮箱";
            case "UNBIND" -> "解除此邮箱与账号的绑定";
            default -> throw new IllegalArgumentException("验证码用途无效");
        };
        String key = buildKey(id, tokenVersion, purpose, email);
        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        String digest = createDigest(key, code);
        String prefix = "acgbox:account-email:{" + id + "}:";
        Long result;
        try {
            result = stringRedisTemplate.execute(RESERVE_SCRIPT,
                    List.of(prefix + "cooldown:" + purpose, prefix + "hourly", key), digest);
        } catch (DataAccessException exception) {
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }
        if (Long.valueOf(0L).equals(result)) {
            throw new BusinessException("验证码发送过于频繁，请 60 秒后重试");
        }
        if (Long.valueOf(-1L).equals(result)) {
            throw new BusinessException("验证码发送次数已达上限，请稍后重试");
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("ACGBox 账号邮箱验证码");
        message.setText("您正在" + operation + "，验证码为：" + code
                + "。验证码 5 分钟内有效，只能使用一次，请勿向他人提供。若非本人操作，请忽略此邮件并检查账号安全。");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            // 发送失败时仅删除本次摘要，保留限流计数，避免故障期间被重复触发发信。
            try {
                stringRedisTemplate.execute(REMOVE_SCRIPT, List.of(key), digest);
            } catch (DataAccessException cleanupException) {
                log.warn("邮件发送失败后的验证码清理未完成，账号 ID：{}", id);
            }
            log.warn("邮箱验证码发送失败，账号 ID：{}，异常类型：{}", id, exception.getClass().getSimpleName());
            throw new BusinessException("验证码邮件发送失败，请稍后重试");
        }
    }

    /**
     * 原子校验并消费验证码，每条验证码最多允许五次错误尝试。
     */
    @Override
    public void verifyAndConsume(Long id, Long tokenVersion, String purpose, String email, String code) {
        checkEnabled();
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new BusinessException("验证码必须为 6 位数字");
        }
        String key = buildKey(id, tokenVersion, purpose, email);
        Long result;
        try {
            result = stringRedisTemplate.execute(VERIFY_SCRIPT, List.of(key), createDigest(key, code));
        } catch (DataAccessException exception) {
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException("验证码错误或已过期，请重新获取");
        }
    }

    /**
     * 换绑凭证使用安全随机数，Redis 仅保存摘要，且原子设置五分钟有效期。
     */
    @Override
    public String createEmailChangeToken(Long id, Long tokenVersion, String email) {
        checkEnabled();
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String emailChangeToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String key = buildKey(id, tokenVersion, "CHANGE_TOKEN", email);
        try {
            Long result = stringRedisTemplate.execute(SAVE_CHANGE_TOKEN_SCRIPT,
                    List.of(key), createDigest(key, emailChangeToken));
            if (!Long.valueOf(1L).equals(result)) {
                throw new BusinessException("验证码服务暂不可用，请稍后重试");
            }
        } catch (DataAccessException exception) {
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }
        return emailChangeToken;
    }

    /**
     * 发送新邮箱验证码时只校验凭证，最终换绑时原子校验并删除凭证。
     */
    @Override
    public void verifyEmailChangeToken(Long id, Long tokenVersion, String email,
                                       String emailChangeToken, boolean consume) {
        checkEnabled();
        if (emailChangeToken == null || !emailChangeToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new BusinessException("请先验证原邮箱后再换绑");
        }
        String key = buildKey(id, tokenVersion, "CHANGE_TOKEN", email);
        Long result;
        try {
            result = stringRedisTemplate.execute(VERIFY_CHANGE_TOKEN_SCRIPT,
                    List.of(key), createDigest(key, emailChangeToken), consume ? "1" : "0");
        } catch (DataAccessException exception) {
            throw new BusinessException("验证码服务暂不可用，请稍后重试");
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new BusinessException("邮箱换绑凭证无效或已过期，请重新验证原邮箱");
        }
    }

    private void checkEnabled() {
        if (!enabled) {
            throw new BusinessException("邮箱验证码功能尚未启用");
        }
    }

    /**
     * 同一账号的键使用相同哈希槽，邮箱摘要保留大小写差异且不在键中暴露邮箱。
     */
    private String buildKey(Long id, Long tokenVersion, String purpose, String email) {
        try {
            String emailDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(email.getBytes(StandardCharsets.UTF_8)));
            return "acgbox:account-email:{" + id + "}:code:" + tokenVersion + ":" + purpose + ":" + emailDigest;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成邮箱摘要", exception);
        }
    }

    /**
     * 摘要包含用途键，避免不同账号、版本、邮箱或用途之间互用验证码。
     */
    private String createDigest(String key, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            return HexFormat.of().formatHex(mac.doFinal(
                    ("account-email\n" + key + "\n" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("无法生成验证码摘要", exception);
        }
    }

    private static RedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }
}
