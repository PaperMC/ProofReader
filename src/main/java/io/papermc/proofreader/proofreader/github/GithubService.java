package io.papermc.proofreader.proofreader.github;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.papermc.proofreader.proofreader.ProofReaderConfig.Config;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.security.PEMDecoder;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class GithubService {

    private final RestClient restClient;
    private final Config config;

    @Nullable
    private Token token = null;

    public GithubService(Config config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("User-Agent", "ProofReader <github.com/PaperMC/ProofReader>")
                .defaultUriVariables(Map.of("owner", config.sourceRepo().owner(), "repo", config.sourceRepo().name()))
                .requestInitializer((request -> request.getHeaders().setBearerAuth(getGitHubToken())))
                .build();
    }

    public long addComment(long prNumber, String comment) {
        record Response(long id) {}

        var response = restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{issue_number}/comments", Map.of("issue_number", prNumber))
                .body(Map.of("body", comment))
                .retrieve()
                .toEntity(Response.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody().id();
        } else {
            throw new RuntimeException("Failed to add comment: " + response.getStatusCode());
        }
    }

    public void updateComment(long commentId, String comment) {
        var response = restClient.patch()
                .uri("/repos/{owner}/{repo}/issues/comments/{comment_id}", Map.of("comment_id", commentId))
                .body(Map.of("body", comment))
                .retrieve()
                .toBodilessEntity();

        if (!response.getStatusCode().is2xxSuccessful() ) {
            throw new RuntimeException("Failed to update: " + response.getStatusCode());
        }
    }

    public void addReaction(long commentId, String reaction) {
        var response = restClient.post()
                .uri("/repos/{owner}/{repo}/issues/comments/{comment_id}/reactions", Map.of("comment_id", commentId))
                .body(Map.of("content", reaction))
                .retrieve()
                .toBodilessEntity();

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to add reaction: " + response.getStatusCode());
        }
    }

    @SuppressWarnings("preview")
    private String getGitHubToken() {
        if (this.token == null || this.token.expires_at().isBefore(Instant.now().plus(1, ChronoUnit.MINUTES))) {
            try {
                var privateKey = (RSAPrivateKey) PEMDecoder.of().decode(config.privateKey());

                var algorithm = Algorithm.RSA256(privateKey);
                var jwt = JWT.create()
                        .withIssuer(config.clientId())
                        .withIssuedAt(Instant.now().minus(60, ChronoUnit.SECONDS))
                        .withExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .sign(algorithm);

                this.token = RestClient.create().post()
                        .uri("https://api.github.com/app/installations/" + config.installationId() + " /access_tokens")
                        .header("Authorization", "Bearer " + jwt)
                        .retrieve()
                        .body(Token.class);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to create GitHub JWT token", ex);
            }
        }
        assert this.token != null;
        return this.token.token;
    }

    record Token(String token, Instant expires_at) {
    }
}
