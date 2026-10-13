package org.killze.acgbox.service.impl;

import org.killze.acgbox.entity.user.UserAccount;
import org.killze.acgbox.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** JWT 签发服务实现类。 */
@Service
public class JwtServiceImpl implements JwtService {

    @Autowired
    private JwtEncoder jwtEncoder;

    /** JWT 签发者。*/
    @Value("${jwt.issuer}")
    private String issuer;

    /** JWT 受众。*/
    @Value("${jwt.audience}")
    private String audience;

    /** JWT 过期时间。*/
    @Value("${jwt.expiration}")
    private Long expiration;

    /** 为已通过登录校验的账号生成登录令牌。 */
    @Override
    public String createToken(UserAccount userAccount) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userAccount.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expiration))
                .claim("role", userAccount.getRole())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}
