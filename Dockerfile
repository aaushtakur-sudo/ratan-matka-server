FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY NoticeServer.java .
COPY results.json .
COPY index.html .
RUN javac -encoding UTF-8 NoticeServer.java
EXPOSE 10000
CMD ["java", "NoticeServer"]
