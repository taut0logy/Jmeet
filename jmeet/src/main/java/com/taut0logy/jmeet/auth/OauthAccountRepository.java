package com.taut0logy.jmeet.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, String> {

    Optional<OauthAccount> findByProviderAndProviderUid(String provider, String providerUid);
}
