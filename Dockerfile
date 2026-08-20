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
COPY common/build.gradle ./common/
COPY riot/build.gradle ./riot/
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
