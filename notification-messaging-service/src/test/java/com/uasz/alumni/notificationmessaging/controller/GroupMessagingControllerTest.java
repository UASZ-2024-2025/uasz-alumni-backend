package com.uasz.alumni.notificationmessaging.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uasz.alumni.notificationmessaging.config.ApiPrefixConfig;
import com.uasz.alumni.notificationmessaging.config.SecurityConfig;
import com.uasz.alumni.notificationmessaging.exceptions.ApiException;
import com.uasz.alumni.notificationmessaging.exceptions.GlobalExceptionHandler;
import com.uasz.alumni.notificationmessaging.jwt.JwtService;
import com.uasz.alumni.notificationmessaging.security.AuthenticatedUser;
import com.uasz.alumni.notificationmessaging.service.ChatUserDirectory;
import com.uasz.alumni.notificationmessaging.service.GroupService;
import com.uasz.alumni.notificationmessaging.spi.model.SendMessageRequest;
import com.uasz.alumni.notificationmessaging.spi.model.Group;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessage;
import com.uasz.alumni.notificationmessaging.spi.model.GroupMessageType;
import com.uasz.alumni.notificationmessaging.spi.model.GroupPreferences;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Routes /messages/groups côté web : filtre JWT réel, seul le parsing du jeton est simulé. */
@WebMvcTest(controllers = GroupMessagingController.class, properties = "security.api.prefix=/v1")
@Import({SecurityConfig.class, ApiPrefixConfig.class, GlobalExceptionHandler.class})
class GroupMessagingControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean GroupService service;
    @MockitoBean ChatUserDirectory directory;
    @MockitoBean JwtService jwtService;

    private final UUID user = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final AuthenticatedUser etudiant = new AuthenticatedUser(user, "e@uasz.sn", "ETUDIANT", "Moussa Ndiaye");

    @BeforeEach
    void jetons() {
        when(jwtService.parse(anyString())).thenReturn(Optional.empty());
        when(jwtService.parse("etudiant")).thenReturn(Optional.of(etudiant));
        when(jwtService.parse("entreprise"))
                .thenReturn(Optional.of(new AuthenticatedUser(user, "rh@sonatel.sn", "ENTREPRISE", "RH Sonatel")));
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void creationDonne201EtEnregistreLeNomDeLAppelant() throws Exception {
        when(service.createGroup(any(), eq(etudiant))).thenReturn(new Group().id(groupId).name("Promo GL"));

        mvc.perform(as("etudiant", json(post("/v1/messages/groups"), "{\"name\":\"Promo GL\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(groupId.toString()));
        verify(directory).remember(etudiant);
    }

    @Test
    void unNomHorsDesBornesDuContratDonne400() throws Exception {
        mvc.perform(as("etudiant", json(post("/v1/messages/groups"), "{\"name\":\"ab\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(as("etudiant", json(post("/v1/messages/groups/" + groupId + "/members"), "{\"memberIds\":[]}")))
                .andExpect(status().isBadRequest());
        verify(service, never()).createGroup(any(), any());
    }

    @Test
    void lesEntreprisesNOntPasAccesAuxGroupes() throws Exception {
        mvc.perform(as("entreprise", get("/v1/messages/groups"))).andExpect(status().isForbidden());
        mvc.perform(as("entreprise", json(post("/v1/messages/groups"), "{\"name\":\"Recrutement\"}")))
                .andExpect(status().isForbidden());
        verify(service, never()).listGroups(any());
    }

    @Test
    void sansJetonOuJetonInvalideDonne401() throws Exception {
        mvc.perform(get("/v1/messages/groups")).andExpect(status().isUnauthorized());
        mvc.perform(as("faux", get("/v1/messages/groups/" + groupId))).andExpect(status().isUnauthorized());
    }

    @Test
    void unNonMembreRecoitLe403DuServiceAuFormatDuContrat() throws Exception {
        when(service.getGroup(groupId, etudiant)).thenThrow(
                new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Vous n'êtes pas membre de ce groupe."));

        mvc.perform(as("etudiant", get("/v1/messages/groups/" + groupId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Vous n'êtes pas membre de ce groupe."));
    }

    @Test
    void envoiDUnMessage201EtSuppression204() throws Exception {
        when(service.sendMessage(groupId, new SendMessageRequest().content("Bonjour"), etudiant)).thenReturn(
                new GroupMessage().id(UUID.randomUUID()).content("Bonjour").type(GroupMessageType.TEXT).deleted(false));

        mvc.perform(as("etudiant", json(post("/v1/messages/groups/" + groupId + "/messages"), "{\"content\":\"Bonjour\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEXT"));
        UUID messageId = UUID.randomUUID();
        mvc.perform(as("etudiant", delete("/v1/messages/groups/" + groupId + "/messages/" + messageId)))
                .andExpect(status().isNoContent());
        verify(service).deleteMessage(groupId, messageId, etudiant);
    }

    @Test
    void preferencesEtRoleSontTransmisAuService() throws Exception {
        when(service.updatePreferences(eq(groupId), any(), eq(etudiant)))
                .thenReturn(new GroupPreferences().pinned(true).archived(false).muted(true));
        mvc.perform(as("etudiant", json(patch("/v1/messages/groups/" + groupId + "/preferences"),
                        "{\"pinned\":true,\"mute\":\"ALWAYS\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted").value(true));

        mvc.perform(as("etudiant", json(patch("/v1/messages/groups/" + groupId + "/members/" + UUID.randomUUID()),
                        "{\"groupRole\":\"OWNER\"}")))
                .andExpect(status().isBadRequest()); // OWNER ne s'attribue pas
        when(service.listGroups(etudiant)).thenReturn(List.of());
        mvc.perform(as("etudiant", get("/v1/messages/groups"))).andExpect(status().isOk());
    }
}
