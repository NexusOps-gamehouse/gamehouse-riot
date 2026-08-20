package gg.duo.riot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
 * DataSource 자동 구성을 끄지 않아도 되는 이유: build.gradle 에 JPA 와 드라이버가
 * 아예 없어서 자동 구성이 켜지지 않는다.
 */
@SpringBootApplication(scanBasePackages = {"gg.duo.riot", "gg.duo.common"})
public class RiotApplication {
    public static void main(String[] args) {
        SpringApplication.run(RiotApplication.class, args);
    }
}
