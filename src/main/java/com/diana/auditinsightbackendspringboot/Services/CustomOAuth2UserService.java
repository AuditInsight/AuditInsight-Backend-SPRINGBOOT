package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import com.diana.auditinsightbackendspringboot.Models.ClientProfile;
import com.diana.auditinsightbackendspringboot.Models.User;
import com.diana.auditinsightbackendspringboot.Repositories.ClientRepository;
import com.diana.auditinsightbackendspringboot.Repositories.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultReactiveOAuth2UserService {

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;

    public CustomOAuth2UserService(UserRepository userRepository, ClientRepository clientRepository) {
        this.userRepository = userRepository;
        this.clientRepository = clientRepository;
    }

    @Override
    public Mono<OAuth2User> loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        return super.loadUser(userRequest).flatMap(oAuth2User -> {
            Map<String, Object> attributes = oAuth2User.getAttributes();
            String email = (String) attributes.get("email");
            String registrationId = userRequest.getClientRegistration().getRegistrationId();

            if (!"google".equals(registrationId)) {
                return Mono.error(new OAuth2AuthenticationException("Unknown provider: " + registrationId));
            }

            String givenName = (String) attributes.get("given_name");
            String familyName = (String) attributes.get("family_name");
            String fullName = (givenName != null ? givenName : "") +
                              (familyName != null ? " " + familyName : "");

            return userRepository.findByUsername(email)
                    .switchIfEmpty(Mono.defer(() -> {
                        User user = new User();
                        user.setFullName(fullName.trim());
                        user.setAuthProvider(registrationId);
                        user.setUsername(email);
                        user.setPassword("");
                        user.setRole(Role.CLIENT);
                        user.setVerified(true);
                        return userRepository.save(user);
                    }))
                    .flatMap(user -> ensureClientProfile(user, email, givenName, familyName).thenReturn(user))
                    .map(user -> new DefaultOAuth2User(
                            Collections.singleton(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                            attributes,
                            "email"
                    ));
        });
    }

    private Mono<ClientProfile> ensureClientProfile(User user, String email, String givenName, String familyName) {
        if (user.getRole() != Role.CLIENT) {
            return Mono.empty();
        }
        return clientRepository.findByEmailAddress(email)
                .switchIfEmpty(Mono.defer(() -> {
                    ClientProfile profile = new ClientProfile();
                    profile.setEmailAddress(email);
                    profile.setFirstName(givenName);
                    profile.setLastName(familyName);
                    return clientRepository.save(profile);
                }));
    }
}
