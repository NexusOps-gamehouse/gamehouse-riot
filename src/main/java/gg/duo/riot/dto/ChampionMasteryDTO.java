package gg.duo.riot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChampionMasteryDTO {

    private Integer ranking;

    private Long championId;

    private Integer championMasteryLevel;

    private Integer championMasteryPoints;
}