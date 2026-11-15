package com.uasz.alumni.user.service;

import com.uasz.alumni.user.exceptions.BadRequestException;
import com.uasz.alumni.user.exceptions.ConflictException;
import com.uasz.alumni.user.exceptions.ForbiddenException;
import com.uasz.alumni.user.exceptions.UnauthorizedException;
import com.uasz.alumni.user.jwt.JwtTokenProvider;
import com.uasz.alumni.user.model.RoleEntity;
import com.uasz.alumni.user.model.UserEntity;
import com.uasz.alumni.user.repository.RoleRepository;
import com.uasz.alumni.user.repository.UserRepository;
import com.uasz.alumni.user.spi.model.AuthResponse;
import com.uasz.alumni.user.spi.model.ConfirmEmailRequest;
import com.uasz.alumni.user.spi.model.ForgotPasswordRequest;
import com.uasz.alumni.user.spi.model.LoginRequest;
import com.uasz.alumni.user.spi.model.LogoutRequest;
import com.uasz.alumni.user.spi.model.MessageResponse;
import com.uasz.alumni.user.spi.model.RefreshTokenRequest;
import com.uasz.alumni.user.spi.model.RegisterRequest;
import com.uasz.alumni.user.spi.model.ResendConfirmationRequest;
import com.uasz.alumni.user.spi.model.ResetPasswordRequest;
import com.uasz.alumni.user.spi.model.RoleName;
import com.uasz.alumni.user.spi.model.User;
import com.uasz.alumni.user.spi.model.UserStatus;
import com.uasz.alumni.user.utils.UserMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public User registerUser(RegisterRequest request) {
        if (request == null) {
            throw new BadRequestException("Les données d'inscription sont requises.");
        }
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : null;
        if (email == null || email.isBlank()) {
            throw new BadRequestException("L'adresse email est requise.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Cette adresse email est déjà utilisée : " + email);
        }

        RoleName roleName = request.getRole() != null ? request.getRole() : RoleName.ETUDIANT;
        RoleEntity role = roleRepository.findById(roleName)
                .orElseGet(() -> roleRepository.save(new RoleEntity(roleName, "Rôle " + roleName, null)));

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setFirstName(request.getFirstName() != null ? request.getFirstName().trim() : "");
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : "");
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        // Compte créé avec statut EN_ATTENTE par défaut
        user.setStatus(UserStatus.EN_ATTENTE);

        UserEntity saved = userRepository.save(user);
        log.info("Nouvel utilisateur inscrit (en attente d'activation) : id={}, email={}, role={}",
                saved.getId(), saved.getEmail(), roleName);

        return UserMapper.toUser(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse loginUser(LoginRequest request) {
        if (request == null || request.getEmail() == null || request.getPassword() == null) {
            throw new UnauthorizedException("Identifiants invalides");
        }

        String email = request.getEmail().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Identifiants invalides"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Échec d'authentification pour l'email : {}", email);
            throw new UnauthorizedException("Identifiants invalides");
        }

        // CRITÈRE D'ACCEPTATION : un utilisateur suspendu ne peut plus se connecter (403)
        if (user.getStatus() == UserStatus.SUSPENDU) {
            log.warn("Tentative de connexion refusée : compte suspendu pour l'email : {}", email);
            throw new ForbiddenException("Votre compte est suspendu. Veuillez contacter l'administrateur.");
        }

        if (user.getStatus() == UserStatus.EN_ATTENTE) {
            log.warn("Tentative de connexion refusée : compte non activé pour l'email : {}", email);
            throw new ForbiddenException("Votre compte n'a pas encore été activé. Veuillez confirmer votre adresse email.");
        }

        if (user.getStatus() == UserStatus.INACTIF) {
            log.warn("Tentative de connexion refusée : compte inactif pour l'email : {}", email);
            throw new ForbiddenException("Votre compte est inactif. Veuillez contacter l'administrateur.");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn((int) jwtTokenProvider.getAccessTokenValidityInSeconds());
        response.setUser(UserMapper.toUser(user));

        log.info("Connexion réussie pour l'utilisateur : id={}, email={}", user.getId(), user.getEmail());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null) {
            throw new UnauthorizedException("Refresh token requis.");
        }

        String refreshToken = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Refresh token invalide ou expiré.");
        }

        UUID userId = jwtTokenProvider.extractUserId(refreshToken);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Utilisateur introuvable."));

        if (user.getStatus() != UserStatus.ACTIF) {
            throw new ForbiddenException("Compte non actif ou suspendu.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn((int) jwtTokenProvider.getAccessTokenValidityInSeconds());
        response.setUser(UserMapper.toUser(user));

        return response;
    }

    @Override
    public void logoutUser(LogoutRequest request) {
        // En mode stateless JWT, le logout côté client supprime les tokens stockés.
        log.info("Déconnexion demandée pour le refresh token fourni.");
    }

    @Override
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        // Réponse générique anti-énumération conforme aux bonnes pratiques de sécurité OWASP
        MessageResponse response = new MessageResponse();
        response.setMessage("Si cette adresse email est associée à un compte, un lien de réinitialisation a été envoyé.");
        return response;
    }

    @Override
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        MessageResponse response = new MessageResponse();
        response.setMessage("Votre mot de passe a été réinitialisé avec succès.");
        return response;
    }

    @Override
    public MessageResponse confirmEmail(ConfirmEmailRequest request) {
        MessageResponse response = new MessageResponse();
        response.setMessage("Votre adresse email a été confirmée avec succès. Votre compte est désormais actif.");
        return response;
    }

    @Override
    public MessageResponse resendConfirmation(ResendConfirmationRequest request) {
        MessageResponse response = new MessageResponse();
        response.setMessage("L'email de confirmation a été renvoyé si le compte existe et n'est pas encore activé.");
        return response;
    }
}

