package gg.duo.riot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;

/**
 * riot 서비스 — 외부 API 게이트웨이.
 *
 * 소유 테이블: 없음.
 *
 * 가장 먼저 떼어낸 서비스다. 리포지토리를 하나도 쓰지 않고 호출하는 곳도
 * user 하나뿐이라 끊을 매듭이 없었다.
 *
 * 이 서비스만 클러스터 밖으로 나간다(api.riotgames.com). 프라이빗 서브넷에서
 * 나가는 경로는 NAT Gateway 다 — 들어오는 요청을 나눠주는 Ingress 와는
 * 반대 방향이고, riot 은 Ingress 규칙이 없어 밖에서는 닿지 않는다.
 *
 * [RabbitAutoConfiguration 을 제외하는 이유]
 * riot 은 이벤트를 발행도 구독도 하지 않는다. 소유 테이블이 없어 다른 서비스에
 * 알릴 상태 변화가 없기 때문이다. 그래서 application.yml 에도 spring.rabbitmq.*
 * 설정이 없다.
 *
 * 그런데 amqp 라이브러리는 common 을 통해 클래스패스에 들어온다. 그러면
 * Spring Boot 가 자동으로 ConnectionFactory 와 RabbitHealthIndicator 를 만들고,
 * 설정이 없으니 기본값(guest/guest)으로 브로커에 접속을 시도한다.
 *   ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN
 *   → actuator/health 가 DOWN → 헬스체크 503
 * 앱은 뜨지만 "건강하지 않은" 상태로 남는다. k8s 라면 readiness 실패로
 * 트래픽을 못 받는다.
 *
 * gamehouse.events.enabled=false 는 우리가 만든 빈(exchange · queue · bridge ·
 * publisher)만 끈다. Spring Boot 의 자동설정까지는 막지 못한다.
 * 쓰지 않는 인프라에 연결을 시도하지 않도록 여기서 아예 제외한다.
 *
 * DataSource 는 제외할 필요가 없다. build.gradle 에 JPA 도 드라이버도 아예 없어서
 * 자동설정이 켜지지 않는다.
 */
@SpringBootApplication(
        scanBasePackages = {"gg.duo.riot", "gg.duo.common"},
        exclude = RabbitAutoConfiguration.class
)
public class RiotApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiotApplication.class, args);
    }
}
