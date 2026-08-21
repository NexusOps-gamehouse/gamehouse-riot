# syntax=docker/dockerfile:1
#
# ⚠️ 빌드 컨텍스트는 backend/ 루트다. 모듈 디렉터리가 아니다.
#     docker build -f riot/Dockerfile -t gamehouse:riot .
#
# 이유: 이 모듈은 :common 을 의존한다. 컨텍스트를 riot/ 으로 잡으면
# settings.gradle 도 common/ 도 보이지 않아 Gradle 이 프로젝트를 찾지 못한다.

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# 의존성 정의 먼저 복사 → 소스만 바뀔 때 빌드 캐시 재사용
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# settings.gradle 이 모듈 5개를 전부 선언하므로, 빌드 대상이 하나여도
# Gradle 은 설정 단계에서 5개 프로젝트를 모두 읽는다.
# 자기 것만 복사하면 나머지 디렉터리가 없어서 이렇게 죽는다.
#   Configuring project ':user' without an existing directory is not allowed
# (Gradle 9 부터 경고가 아니라 에러다)
#
# build.gradle 은 몇 줄짜리라 5개를 다 복사해도 레이어가 거의 안 커지고,
# 거의 바뀌지 않으므로 캐시도 그대로 유지된다.
# src 는 아래에서 필요한 것만 복사한다 — 그게 캐시를 좌우하는 부분이다.
COPY common/build.gradle ./common/
COPY user/build.gradle   ./user/
COPY post/build.gradle   ./post/
COPY chat/build.gradle   ./chat/
COPY riot/build.gradle   ./riot/
RUN chmod +x gradlew

# 의존성만 먼저 받아 별도 레이어로 캐시. src와 분리 → 소스만 바뀌면 이 레이어 재사용.
# (캐시 마운트 대신 레이어라 CI의 cache-from: type=gha 로 런 간 유지됨)
RUN ./gradlew --no-daemon :riot:dependencies

COPY common/src ./common/src
COPY riot/src ./riot/src
RUN ./gradlew --no-daemon :riot:bootJar
RUN cp riot/build/libs/*.jar /app/app.jar && mkdir -p /out/uploads

# 최소 JRE 생성. 새 의존성이 다른 JDK 모듈을 요구하면 여기 추가 (빠지면 런타임 NoClassDefFound)
RUN jlink \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.cryptoki,jdk.crypto.ec,jdk.jfr,jdk.management,jdk.unsupported,jdk.httpserver \
      --strip-debug --no-header-files --no-man-pages --compress=2 \
      --output /javaruntime

FROM debian:bookworm-slim AS run
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring

COPY --from=build /javaruntime ${JAVA_HOME}
COPY --from=build /app/app.jar /app/app.jar
# 볼륨이 소유권을 상속받아 쓰기 가능하도록 spring 소유로 준비
COPY --from=build --chown=spring:spring /out/uploads /app/uploads

USER spring
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
