FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY NoticeServer.java .

RUN javac -encoding UTF-8 NoticeServer.java

EXPOSE 8081

CMD ["java", "NoticeServer"]
