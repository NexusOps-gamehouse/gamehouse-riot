package gg.duo.riot.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeagueResponseDTO {
    private String queueType;
    private String tier;         // DIAMOND, PLATINUM, GOLD
    private String rank;         // I, II, III, IV
    private int leaguePoints;    // LP 점수
    private int wins;            // 승리 횟수
    private int losses;          // 패배 횟수
}
