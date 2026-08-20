package gg.duo.riot.controller;

import gg.duo.common.exception.BusinessException;
import gg.duo.common.exception.ErrorCode;
import gg.duo.riot.dto.response.RiotProfileResponseDTO;
import gg.duo.riot.service.RateLimiter;
import gg.duo.riot.service.RiotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 라이엇 조회 API — 클러스터 내부 전용. Ingress 에 노출하지 않는다.
 *
 * 예전에는 user 서비스가 RiotService 를 직접 주입받아 함수로 불렀다.
 * 그 호출이 이제 이 엔드포인트를 지난다. 바뀐 것은 경로뿐이고,
 * 응답 형태(RiotProfileResponseDTO)는 그대로다 — user 쪽 RiotProfileView 가
 * 같은 JSON 을 자기 형태로 읽는다.
 */
@RestController
@RequestMapping("/internal/riot")
@RequiredArgsConstructor
public class RiotController {

    private final RiotService riotService;
    private final RateLimiter rateLimiter;

    /** Riot ID → 프로필 (Account API 부터 탄다) */
    @GetMapping("/profile")
    public RiotProfileResponseDTO profile(@RequestParam String gameName,
                                          @RequestParam String tagLine) {
        guard();
        return riotService.fetchProfile(gameName, tagLine);
    }

    /** puuid 를 이미 알 때 — Account API 호출을 건너뛴다 */
    @GetMapping("/profile/by-puuid")
    public RiotProfileResponseDTO profileByPuuid(@RequestParam String puuid,
                                                 @RequestParam String gameName,
                                                 @RequestParam String tagLine) {
        guard();
        return riotService.fetchProfileByPuuid(puuid, gameName, tagLine);
    }

    /**
     * 호출 예산을 먼저 확인한다.
     *
     * 라이엇에 실제로 부딪혀 429 를 받아도 결과는 같지만, 그 시도 자체가
     * 예산을 태운다. 한도에 닿았을 때 조용히 멈추는 편이 회복이 빠르다.
     */
    private void guard() {
        if (!rateLimiter.tryAcquire()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "라이엇 요청이 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
