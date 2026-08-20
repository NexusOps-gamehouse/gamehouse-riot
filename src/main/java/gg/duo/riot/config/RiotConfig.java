package gg.duo.riot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Configuration
public class RiotConfig {

    @Value("${riot.api.regional-url}")
    private String regionalUrl;

    @Value("${riot.api.platform-url}")
    private String platformUrl;

    /**
     * WebClient.builder() (static) 대신 주입받은 WebClient.Builder 를 사용한다.
     *
     * static builder 는 아무것도 붙지 않은 빈 빌더라 Micrometer 계측이 적용되지 않는다.
     * Spring Boot 가 자동 구성해 두는 WebClient.Builder 빈을 써야
     * http_client_requests_seconds_* (호출량 / 응답시간 / 상태코드) 지표가 기록된다.
     * Riot API 429(레이트 리밋) 감지가 여기에 달려 있다.
     */
    @Bean("regionalWebClient")
    public WebClient regionalWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(regionalUrl)
                .filter(rateLimitFilter())
                .build();
    }

    @Bean("platformWebClient")
    public WebClient platformWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(platformUrl)
                .filter(rateLimitFilter())
                .build();
    }

    /**
     * [Riot 429 를 우리 429 로 옮겨 담는다]
     *
     * HTTP 대화가 두 개라는 점이 핵심이다.
     *   ① 브라우저 ↔ 우리 백엔드
     *   ② 우리 백엔드 ↔ Riot
     * ②에서 받은 429 는 ①의 상태 코드와 아무 관계가 없다. 우리가 명시적으로
     * 옮겨 담지 않으면 사라진다.
     *
     * 전에는 이랬다.
     *   Riot 429 → retrieve() 가 WebClientResponseException.TooManyRequests 를 던짐
     *            → 잡는 곳이 없음 → GlobalExceptionHandler 에도 해당 없음
     *            → Spring 기본 동작으로 500
     * 그래서 프론트 riotErrMsg 의 `status === 429` 분기는 문법상 멀쩡한데
     * 실행될 일이 없는 죽은 코드였고, 사용자는 "잠시 후 다시 시도해 주세요"라는
     * 엉뚱한 안내를 받았다. (404 는 이 옮겨 담기를 이미 해둬서 정상 동작한다.)
     *
     * 관측 쪽 효과가 더 크다. 429 가 500 으로 새어 나가면 "5xx 에러 발생" 알람이
     * 울리고 대시보드에도 서버 장애로 기록된다. 우리 코드가 터진 게 아닌데도.
     * 5xx 는 0 이 기준선이어야 알람이 값어치가 있다. 404 를 걷어냈던 것과 같은 이유다.
     *
     * 429 는 2분이 지나면 저절로 풀린다. 사람이 할 일이 없으므로 알람 대상이 아니고,
     * 사용자에게 안내 문구만 보여주면 된다. 추세는 'Riot 호출 횟수 (2분/100회 한도)'
     * 패널이 이미 보여준다 — 429 시리즈에 빨간색이 지정돼 있고, 애초에 총합 선이
     * 한도에 닿기 전에 미리 보인다.
     *
     * ⚠️ 401/403(키 만료)은 일부러 건드리지 않았다. 기다려도 복구되지 않고 사람이
     *    키를 재발급해야 하므로, 500 으로 남아 5xx 에 잡히는 것이 맞다.
     *    '에러 발생 건수' 패널에서 전체 / Riot 키 만료 / 엔드포인트 세 숫자가
     *    맞아떨어지는 것도 그 덕분이다.
     *
     * ResponseStatusException 은 Spring 이 그대로 상태 코드로 바꿔 준다.
     * 새 예외 클래스도 GlobalExceptionHandler 수정도 필요 없다.
     * 본문 메시지는 넣지 않는다 — Spring Boot 기본값(server.error.include-message=never)
     * 이라 어차피 전달되지 않고, 프론트에 이미 문구가 있다.
     *
     * 필터를 두 WebClient 에 다는 이유: RiotApiClient 의 메서드 4개에 각각 붙이면
     * 새 호출을 추가할 때 빠뜨리기 쉽다. 여기 한 곳이면 전부 자동으로 적용된다.
     */
    private ExchangeFilterFunction rateLimitFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            if (response.statusCode().value() == 429) {
                // 응답 본문을 비워 커넥션을 반납한 뒤 예외로 바꾼다.
                // releaseBody() 를 빼면 커넥션이 반납되지 않아 풀이 마른다.
                return response.releaseBody()
                        .then(Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)));
            }
            return Mono.just(response);
        });
    }

}
