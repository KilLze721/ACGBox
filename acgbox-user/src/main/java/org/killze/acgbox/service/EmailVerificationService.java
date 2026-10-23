package org.killze.acgbox.service;

/**
 * 邮箱验证码服务接口，验证码绑定账号、令牌版本、用途和邮箱。
 */
public interface EmailVerificationService {

    /**
     * 限流后生成验证码并发送邮件。
     *
     * @param id 账号 ID，登录验证码从数据库查询，其他用途从当前登录身份获取
     * @param tokenVersion 账号当前令牌版本号
     * @param purpose 验证用途
     * @param email 接收验证码的邮箱
     */
    void sendCode(Long id, Long tokenVersion, String purpose, String email);

    /**
     * 校验并一次性消费验证码，错误次数达到上限后使其失效。
     *
     * @param id 账号 ID，登录验证码从数据库查询，其他用途从当前登录身份获取
     * @param tokenVersion 账号当前令牌版本号
     * @param purpose 验证用途
     * @param email 接收验证码的邮箱
     * @param code 邮箱验证码
     */
    void verifyAndConsume(Long id, Long tokenVersion, String purpose, String email, String code);

    /**
     * 原邮箱验证成功后生成有效期五分钟的换绑凭证，新的凭证覆盖旧凭证。
     *
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @param email 当前绑定的原邮箱
     * @return 邮箱换绑凭证
     */
    String createEmailChangeToken(Long id, Long tokenVersion, String email);

    /**
     * 校验绑定账号、版本和原邮箱的换绑凭证，完成换绑时一次性消费。
     *
     * @param id 当前登录账号 ID
     * @param tokenVersion 当前登录令牌版本号
     * @param email 当前绑定的原邮箱
     * @param emailChangeToken 邮箱换绑凭证
     * @param consume 是否消费凭证
     */
    void verifyEmailChangeToken(Long id, Long tokenVersion, String email, String emailChangeToken, boolean consume);
}
