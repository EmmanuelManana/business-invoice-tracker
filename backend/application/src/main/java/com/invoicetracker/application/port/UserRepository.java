package com.invoicetracker.application.port;

import com.invoicetracker.domain.user.AppUser;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<AppUser> findById(UUID id);

    Optional<AppUser> findByEmail(String normalisedEmail);

    boolean existsByEmail(String normalisedEmail);

    AppUser save(AppUser user);
}
