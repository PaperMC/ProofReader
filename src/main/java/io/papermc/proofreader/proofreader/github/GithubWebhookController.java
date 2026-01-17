package io.papermc.proofreader.proofreader.github;

import io.papermc.proofreader.proofreader.github.Model.*;
import io.papermc.proofreader.proofreader.service.BuildService;
import io.papermc.proofreader.proofreader.service.CommentService;
import io.papermc.proofreader.proofreader.service.StateService;
import org.apache.tomcat.util.buf.HexUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;

import static io.papermc.proofreader.proofreader.ProofReaderConfig.Config;
import static io.papermc.proofreader.proofreader.service.StateService.MainState;

@RestController
@RequestMapping("/github/webhook")
class GithubWebhookController {

    private final CommentService comments;
    private final BuildService builds;
    private final StateService states;
    private final GithubService github;
    private final Config config;
    private final ObjectMapper objectMapper;

    GithubWebhookController(CommentService comments, BuildService builds, StateService states, GithubService github, Config config, ObjectMapper objectMapper) {
        this.comments = comments;
        this.builds = builds;
        this.states = states;
        this.github = github;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @PostMapping(headers = "X-GitHub-Event=ping")
    public String handlePing(@RequestBody String rawPayload, @RequestHeader("X-Hub-Signature-256") String signature) {
        var payload = validateAndParsePayload(rawPayload, signature, PingPayload.class);
        System.out.println("GitHub Ping received: " + payload.zen());
        return "Pong";
    }

    @PostMapping(headers = "X-GitHub-Event=pull_request")
    public void handlePullRequest(@RequestBody String rawPayload, @RequestHeader("X-Hub-Signature-256") String signature) {
        var payload = validateAndParsePayload(rawPayload, signature, PullRequestPayload.class);
        checkRepo(payload.repository());

        if (payload.action() != Action.OPENED && payload.action() != Action.SYNCHRONIZE) {
            return;
        }

        var state = states.getState(payload.number());
        state.firstTimer = payload.pull_request().author_association() == AuthorAssociation.FIRST_TIMER;
        if (state.firstTimer && !state.approved) {
            states.updateState(state);
            return;
        }

        builds.triggerBuild(state);
        states.updateState(state);
    }

    @PostMapping(headers = "X-GitHub-Event=issue_comment")
    public void handleIssueComment(@RequestBody String rawPayload, @RequestHeader("X-Hub-Signature-256") String signature) {
        var payload = validateAndParsePayload(rawPayload, signature, IssueCommentPayload.class);
        checkRepo(payload.repository());

        var state = states.getState(payload.issue().number());
        if (payload.action() == Action.CREATED && hasPerms(payload.comment().author_association())) {
            if (payload.comment().body().trim().equalsIgnoreCase("/force-update")) {
                builds.triggerBuild(state);
                github.addReaction(payload.comment().id(), "+1");
            } else if (payload.comment().body().trim().equalsIgnoreCase("/rebase")) {
                builds.triggerRebase(state);
                github.addReaction(payload.comment().id(), "+1");
            }
        } else if (payload.action() == Action.EDITED && payload.comment().id() == state.commentId) {
            var checkedBoxes = comments.newCheckedBoxes(payload.changes().body().from(), payload.comment().body());
            for (String check : checkedBoxes) {
                if (check.contains("rebase")) {
                    builds.triggerRebase(state);
                } else if (check.contains("force update")) {
                    builds.triggerBuild(state);
                } else if (check.contains("approve")) {
                    state.approved = true;
                    builds.triggerBuild(state);
                }
            }
        }
    }

    @PostMapping(headers = "X-GitHub-Event=push")
    public void handlePushEvent(@RequestBody String rawPayload, @RequestHeader("X-Hub-Signature-256") String signature) {
        var payload = validateAndParsePayload(rawPayload, signature, PushPayload.class);
        checkRepo(payload.repository());

        if (payload.ref().equals("refs/heads/main")) {
            var state = new MainState();
            builds.triggerBuild(state);
        }
    }

    @PostMapping
    public void handleWebhook(@RequestBody String rawPayload, @RequestHeader("X-Hub-Signature-256") String signature, @RequestHeader("X-GitHub-Event") String event) {
        var payload = validateAndParsePayload(rawPayload, signature, JsonNode.class);
//        System.out.println("handler called for event: " + event);
//        System.out.println(node);
    }

    private final EnumSet<AuthorAssociation> permittedAssociations = EnumSet.of(
            AuthorAssociation.COLLABORATOR,
            AuthorAssociation.MEMBER,
            AuthorAssociation.OWNER
    );
    private boolean hasPerms(AuthorAssociation authorAssociation) {
        return permittedAssociations.contains(authorAssociation);
    }

    private void checkRepo(Repository repo) {
        if (!repo.full_name().equals(config.sourceRepo().withSlash())) {
            throw new IllegalArgumentException("Received webhook for invalid repository: " + repo.full_name() + " (expected " + config.sourceRepo().withSlash() + ")" );
        }
    }

    private <T> T validateAndParsePayload(String rawPayload, String signature, Class<T> clazz) {
        try {
            var hmac = Mac.getInstance("HmacSHA256");
            var keySpec = new SecretKeySpec(config.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(keySpec);
            var computedHash = hmac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            var provided = HexUtils.fromHexString(signature.replaceFirst("sha256=", ""));
            if (!MessageDigest.isEqual(computedHash, provided)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
            }
            return objectMapper.readValue(rawPayload, clazz);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
