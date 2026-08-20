package gg.duo.riot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 라이엇 API 호출 총량 제한.
 *
 * 개발용 키 한도가 '2분당 100회'다. user 서비스의 쿨다운(사용자당 2분 1회)과
 * 성격이 다르다 — 저쪽은 한 사람이 연타하는 것을 막고, 이쪽은 사용자가 여럿일 때
 * 전체 합이 한도를 넘지 않게 막는다. 사용자 50명이 동시에 한 번씩만 눌러도
 * 호출은 150~200회다.
 *
 * 한도를 넘으면 라이엇을 부르지 않고 바로 429 를 돌려준다. 실제로 부딪혀서
 * 429 를 받는 것과 결과는 같지만, 우리 예산을 태우지 않는다는 점이 다르다.
 *
 * ⚠️ 지금 구현은 프로세스 메모리 안의 창(window)이다. riot 파드를 2개 이상으로
 *    늘리면 파드마다 자기 몫만 세므로 합계가 한도를 넘는다. 그때는 Redis 토큰
 *    버킷으로 바꿔야 한다(설계서의 RedisConfig). 지금은 인프라에 Redis 가 없어
 *    추가하지 않았다 — 파드 1개로 운영하는 동안은 이 구현으로 정확하다.
 */
@Slf4j
@Component
public class RateLimiter {

    /** 라이엇 개발용 키 한도: 2분당 100회. 안전 여유를 두고 90 으로 잡는다. */
    private static final int LIMIT = 90;
    private static final Duration WINDOW = Duration.ofMinutes(2);

    private final Deque<Instant> callTimestamps = new ArrayDeque<>();

    /** 호출해도 되면 true. 한도를 넘었으면 false. */
    public synchronized boolean tryAcquire() {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!callTimestamps.isEmpty() && callTimestamps.peekFirst().isBefore(cutoff)) {
            callTimestamps.pollFirst();
        }
        if (callTimestamps.size() >= LIMIT) {
            log.warn("라이엇 호출 한도 도달 — {}분 창에서 {}회. 호출을 보류한다.",
                    WINDOW.toMinutes(), callTimestamps.size());
            return false;
        }
        callTimestamps.addLast(Instant.now());
        return true;
    }

    /** 현재 창에서 지금까지 쓴 호출 수. 지표로 내보낼 때 쓴다. */
    public synchronized int used() {
        return callTimestamps.size();
    }
}
