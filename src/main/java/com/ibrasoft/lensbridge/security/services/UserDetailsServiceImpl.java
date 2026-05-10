package com.ibrasoft.lensbridge.security.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ibrasoft.lensbridge.model.auth.User;
import com.ibrasoft.lensbridge.repository.auth.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
  @Autowired
  UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(username)
        .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

    List<GrantedAuthority> authorities = user.getRoles().stream()
      .map(SimpleGrantedAuthority::new)
      .collect(Collectors.toList());

    return org.springframework.security.core.userdetails.User
      .withUsername(user.getEmail())
      .password(user.getPassword())
      .authorities(authorities)
      .build();
  }

}
