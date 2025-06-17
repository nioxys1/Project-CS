FROM openjdk:21-jdk

LABEL maintainer="yourname@example.com"
LABEL description="RPC Voting App"

WORKDIR /app

COPY build/ /app/
COPY data/ /app/data/

EXPOSE 12345

CMD ["java", "VotingServer"]
