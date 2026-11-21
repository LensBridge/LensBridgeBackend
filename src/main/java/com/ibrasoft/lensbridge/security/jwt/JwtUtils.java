package com.ibrasoft.lensbridge.security.jwt;

import java.security.Key;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {
  private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

  @Value("${lensbridge.app.jwtSecret}")
  private String jwtSecret;

  @Value("${lensbridge.app.jwtExpirationMs}")
  private long jwtExpirationMs;

  /** Decoded once on first use: every request that carries a token validates against it. */
  private volatile Key signingKey;

  /** Issues an access token for the given subject (the user's email). */
  public String generateJwtToken(String username) {
    return Jwts.builder()
        .setSubject(username)
        .setIssuedAt(new Date())
        .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
        .signWith(key(), SignatureAlgorithm.HS256)
        .compact();
  }

  private Key key() {
    Key key = signingKey;
    if (key == null) {
      key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
      signingKey = key;
    }
    return key;
  }

  public String getUserNameFromJwtToken(String token) {
    return Jwts.parserBuilder().setSigningKey(key()).build()
               .parseClaimsJws(token).getBody().getSubject();
  }

  /**
   * A token that fails validation is routine (expired sessions, stale clients, scanners), not
   * a server fault, so each failure is one line without a stack trace. JwtException covers
   * every jjwt failure, including a bad signature, which used to escape to the caller.
   */
  public boolean validateJwtToken(String authToken) {
    try {
      Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(authToken);
      return true;
    } catch (ExpiredJwtException e) {
      logger.debug("JWT token is expired: {}", e.getMessage());
    } catch (JwtException | IllegalArgumentException e) {
      logger.warn("Rejected JWT token: {}", e.getMessage());
    }

    return false;
  }
}
