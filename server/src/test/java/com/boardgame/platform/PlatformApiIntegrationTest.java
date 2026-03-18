package com.boardgame.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlatformApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void guestToRoomToResultFlowWorks() throws Exception {
        AuthSessionResponse first = guestLogin("device-a", "Leader");
        AuthSessionResponse second = guestLogin("device-b", "Member");

        bindPhone(first.token(), "+8613800000000");

        PartyView party = read(post("/api/v1/party")
                .header("X-Auth-Token", first.token()), PartyView.class);
        assertEquals(first.user().id(), party.leaderUserId());

        PartyView updatedParty = read(post("/api/v1/party/" + party.id() + "/invite")
                .header("X-Auth-Token", first.token())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PartyInviteRequest(second.user().id()))), PartyView.class);
        assertEquals(2, updatedParty.members().size());

        RoomView room = read(post("/api/v1/rooms")
                .header("X-Auth-Token", first.token())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new CreateRoomRequest(
                        PlatformStore.GameType.AZUL,
                        2,
                        PlatformStore.RoomVisibility.PUBLIC,
                        true))), RoomView.class);
        assertEquals(2, room.players().size());

        RoomView startedRoom = read(post("/api/v1/rooms/" + room.id() + "/start")
                .header("X-Auth-Token", first.token()), RoomView.class);
        assertNotNull(startedRoom.matchId());

        GameSnapshotView snapshot = read(get("/api/v1/games/" + startedRoom.matchId())
                .header("X-Auth-Token", first.token()), GameSnapshotView.class);
        assertEquals(startedRoom.matchId(), snapshot.gameId());
        assertNotNull(snapshot.currentPlayerId());

        MatchResultView terminated = read(post("/api/v1/ops/matches/" + startedRoom.matchId() + "/terminate")
                .header("X-Ops-Token", "dev-ops-token")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new TerminateMatchRequest("integration test finished"))), MatchResultView.class);
        assertEquals(startedRoom.matchId(), terminated.matchId());
        assertNotNull(terminated.finalState());

        MatchResultView result = read(get("/api/v1/matches/" + startedRoom.matchId() + "/result")
                .header("X-Auth-Token", second.token()), MatchResultView.class);
        assertEquals(2, result.placements().size());
        assertNotNull(result.finalState());

        RankingEntryView[] rankings = read(get("/api/v1/rankings/azul"), RankingEntryView[].class);
        assertEquals(2, rankings.length);
        assertTrue(rankings[0].totalGames() >= 1);
    }

    @Test
    void queueFlowMatchesTwoPartiesIntoOneGame() throws Exception {
        AuthSessionResponse first = guestLogin("qa", "A");
        AuthSessionResponse second = guestLogin("qb", "B");
        AuthSessionResponse third = guestLogin("qc", "C");
        AuthSessionResponse fourth = guestLogin("qd", "D");

        PartyView partyOne = read(post("/api/v1/party").header("X-Auth-Token", first.token()), PartyView.class);
        read(post("/api/v1/party/" + partyOne.id() + "/invite")
                .header("X-Auth-Token", first.token())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PartyInviteRequest(second.user().id()))), PartyView.class);

        PartyView partyTwo = read(post("/api/v1/party").header("X-Auth-Token", third.token()), PartyView.class);
        read(post("/api/v1/party/" + partyTwo.id() + "/invite")
                .header("X-Auth-Token", third.token())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new PartyInviteRequest(fourth.user().id()))), PartyView.class);

        QueueStatusView waiting = read(post("/api/v1/party/" + partyOne.id() + "/queue")
                .header("X-Auth-Token", first.token())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new QueuePartyRequest(PlatformStore.GameType.AZUL, 4))), QueueStatusView.class);
        assertEquals("WAITING", waiting.status());

        QueueStatusView matched = read(post("/api/v1/party/" + partyTwo.id() + "/queue")
                .header("X-Auth-Token", third.token())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new QueuePartyRequest(PlatformStore.GameType.AZUL, 4))), QueueStatusView.class);
        assertEquals("MATCHED", matched.status());
        assertNotNull(matched.roomId());
        assertNotNull(matched.matchId());

        RoomView room = read(get("/api/v1/rooms/" + matched.roomId())
                .header("X-Auth-Token", first.token()), RoomView.class);
        assertEquals(4, room.players().size());
        assertEquals("IN_GAME", room.status());
    }

    private AuthSessionResponse guestLogin(String deviceId, String displayName) throws Exception {
        return read(post("/api/v1/auth/guest")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new GuestAuthRequest(deviceId, displayName))), AuthSessionResponse.class);
    }

    private void bindPhone(String token, String phone) throws Exception {
        read(post("/api/v1/auth/bind-phone")
                .header("X-Auth-Token", token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new BindPhoneRequest(phone))), AuthSessionResponse.class);
    }

    private <T> T read(org.springframework.test.web.servlet.RequestBuilder builder, Class<T> type) throws Exception {
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsByteArray(), type);
    }
}
