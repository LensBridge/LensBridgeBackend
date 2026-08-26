package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
  private final UserRepository userRepository;
  private final AuthorityResolver authorityResolver;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(username)
        .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

    // AuthenticatedUser rather than the plain Spring principal so the account's token
    // generation rides along; AuthTokenFilter needs it on every request and this lookup is
    // the one it already pays for.
    return new AuthenticatedUser(
        user.getEmail(),
        user.getPassword(),
        user.isVerified(),
        authorityResolver.resolveAuthorities(user),
        user.getTokenVersion());
  }

}
