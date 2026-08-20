package gg.duo.riot.dto.response;

import gg.duo.riot.dto.ChampionMasteryDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RiotProfileResponseDTO {

    // Account API
    private String puuid;
    private String gameName;
    private String tagLine;

    // Summoner API
    private Integer profileIconId;
    private Long summonerLevel;

    // League API
    private String tier;
    private String rank;
    private Integer leaguePoints;
    private Integer wins;
    private Integer losses;

    // Champion Mastery API
    private List<ChampionMasteryDTO> championMasteries;

    /**
     * 마지막으로 라이엇을 실제 호출해 갱신한 시각.
     *
     * 프론트가 쿨다운 남은 시간을 계산하는 데 쓴다. 서버가 "몇 초 남았다"를
     * 내려주지 않고 시각만 주는 이유는, 페이지를 새로고침하거나 다른 기기에서
     * 열어도 같은 기준으로 계산되기 때문이다.
     */
    private Instant riotSyncedAt;
}