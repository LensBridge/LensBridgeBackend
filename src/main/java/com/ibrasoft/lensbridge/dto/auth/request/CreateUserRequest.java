package com.ibrasoft.lensbridge.dto.auth.request;

import com.ibrasoft.lensbridge.model.board.Audience;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * An administrator creating an account for somebody else.
 * <p>
 * Distinct from {@link SignupRequest} solely because of the password: on self-signup it is
 * mandatory, and here it is optional. Relaxing it on {@code SignupRequest} instead would
 * have let anyone register a passwordless account through the public endpoint.
 * <p>
 * Leaving the password out is the normal case — the account is created disabled and the
 * invitee sets their own password from the reset email. Supplying one is for the rare case
 * where the account has to be usable immediately, and hands the administrator a credential
 * they had to invent and then transmit somehow.
 */
@Data
public class CreateUserRequest {

  @NotBlank
  @Size(max = 20)
  @Pattern(regexp = "^[A-Za-z]+([ '-][A-Za-z]+)*$", message =
  "First name can only contain letters, spaces, hyphens, and apostrophes")
  private String firstName;

  @NotBlank
  @Size(max = 20)
  @Pattern(regexp = "^[A-Za-z]+([ '-][A-Za-z]+)*$", message = "Last name can only contain letters, spaces, hyphens, and apostrophes")
  private String lastName;

  @NotNull
  private Audience audience;

  @NotBlank
  @Size(max = 50)
  @Email
  // @Pattern(regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]*\\.utoronto\\.ca$", message = "Email must be a valid University of Toronto email address (*.utoronto.ca)")
  private String email;

  @Size(min = 6, max = 40, message = "Password must be between 6 and 40 characters long")
  @Schema(description = "Optional. Omit it to create a disabled account and email the user a "
          + "password reset link; supply it to create an account that can sign in right away.",
          requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  private String password;

  public void setEmail(String email) {
    this.email = email.toLowerCase();
  }

  /** Blank is treated as absent, so {@code ""} invites rather than failing @Size validation. */
  public void setPassword(String password) {
    this.password = (password == null || password.isBlank()) ? null : password;
  }

  @Schema(hidden = true)
  public boolean hasPassword() {
    return password != null;
  }
}
