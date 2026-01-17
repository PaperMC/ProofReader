package io.papermc.proofreader.proofreader.github;

import io.papermc.proofreader.proofreader.github.Model.IssueCommentPayload;
import io.papermc.proofreader.proofreader.github.Model.PingPayload;
import io.papermc.proofreader.proofreader.github.Model.PullRequestPayload;
import io.papermc.proofreader.proofreader.service.BuildService;
import io.papermc.proofreader.proofreader.service.CommentService;
import io.papermc.proofreader.proofreader.service.StateService;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.util.EnumSet;

import static io.papermc.proofreader.proofreader.ProofReaderConfig.*;
import static io.papermc.proofreader.proofreader.github.Model.*;
import static io.papermc.proofreader.proofreader.service.StateService.*;

@RestController
@RequestMapping("/github/webhook")
class GithubWebhookController {

    private final CommentService comments;
    private final BuildService builds;
    private final StateService states;
    private final GithubService github;
    private final Config config;

    GithubWebhookController(CommentService comments, BuildService builds, StateService states, GithubService github, Config config) {
        this.comments = comments;
        this.builds = builds;
        this.states = states;
        this.github = github;
        this.config = config;
    }

    @PostMapping(headers = "X-GitHub-Event=ping")
    public String handlePing(@RequestBody PingPayload payload) {
        System.out.println("GitHub Ping received: " + payload.zen());
        return "Pong";
    }

    @PostMapping(headers = "X-GitHub-Event=pull_request")
    public void handlePullRequest(@RequestBody PullRequestPayload payload) {
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
    public void handleIssueComment(@RequestBody IssueCommentPayload payload) {
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
    public void handlePushEvent(@RequestBody PushPayload payload) {
        checkRepo(payload.repository());

        if (payload.ref().equals("refs/heads/main")) {
            var state = new MainState();
            builds.triggerBuild(state);
        }
    }

    @PostMapping
    public void handleWebhook(@RequestBody JsonNode node, @RequestHeader("X-GitHub-Event") String event) {
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
}
