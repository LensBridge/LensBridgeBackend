package com.ibrasoft.lensbridge.security.jwt;

import java.security.Key;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

  /**
   * Generation counter for the account the token was minted for. Compared against
   * {@code users.token_version} on every request; see {@link com.ibrasoft.lensbridge.model.auth.User}.
   */
  public static final String TOKEN_VERSION_CLAIM = "tv";

  @Value("${lensbridge.app.jwtSecret}")
  private String jwtSecret;

  @Value("${lensbridge.app.jwtExpirationMs}")
  private long jwtExpirationMs;

  /**
   * Mints an access token for {@code authentication}, stamped with the account's current
   * {@code tokenVersion}.
   * <p>
   * The version is a required argument rather than something looked up here so that a caller
   * cannot mint an unrevokable token by forgetting to supply it: a missing claim is treated as
   * stale by {@code AuthTokenFilter}, but only if the caller is forced to think about it at all.
   * <p>
   * Deliberately no authorities in the payload. Roles and permissions are re-resolved from the
   * database on each request, so a demotion takes effect immediately instead of at expiry.
   */
  public String generateJwtToken(Authentication authentication, long tokenVersion) {
    Date now = new Date();
    return Jwts.builder()
        .setSubject(authentication.getName())
        .claim(TOKEN_VERSION_CLAIM, tokenVersion)
        .setIssuedAt(now)
        .setExpiration(new Date(now.getTime() + jwtExpirationMs))
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  private Key key() {
    return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
  }

  public String getUserNameFromJwtToken(String token) {
    return Jwts.parserBuilder().setSigningKey(key()).build()
               .parseClaimsJws(token).getBody().getSubject();
  }

  /**
   * Reads the token-version claim.
   * <p>
   * Returns {@code -1} when the claim is absent or not a number — the case for tokens minted
   * before this claim existed, and for anything malformed. No account's counter can ever hold
   * {@code -1}, so those tokens compare unequal and are rejected. That logs out every session
   * once, at deploy, which is the correct trade for not honouring tokens whose revocation state
   * cannot be established.
   */
  public long getTokenVersionFromJwtToken(String token) {
    Object claim = Jwts.parserBuilder().setSigningKey(key()).build()
        .parseClaimsJws(token).getBody().get(TOKEN_VERSION_CLAIM);
    if (claim instanceof Number number) {
      return number.longValue();
    }
    return -1L;
  }

  public boolean validateJwtToken(String authToken) {
    try {
      Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
      return true;
    } catch (MalformedJwtException e) {
      logger.error("Invalid JWT token: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      logger.error("JWT token is expired: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      logger.error("JWT token is unsupported: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      logger.error("JWT claims string is empty: {}", e.getMessage());
    }

    return false;
  }
}
