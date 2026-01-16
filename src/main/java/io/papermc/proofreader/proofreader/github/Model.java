package io.papermc.proofreader.proofreader.github;

import java.util.List;

public class Model {
    record PingPayload(String zen, String hook_id, User sender, Repository repository, Hook hook) {
        record Hook(long id, String type, String name, List<String> events, Config config) {
            record Config(String url, String content_type) {
            }
        }
    }

    record IssueCommentPayload(Action action, Changes changes, Issue issue, Comment comment, Repository repository,
                               User sender) {
        record Issue(long id, long number, String title, User user, State state, AuthorAssociation author_association,
                     String body) {
        }

        record Comment(long id, String body, User user, AuthorAssociation author_association) {
        }

        record Changes(Body body) {
            record Body(String from) {
            }
        }
    }

    record PullRequestPayload(Action action, long number, PullRequest pull_request, Repository repository,
                              User sender) {
        record PullRequest(long id, long number, String title, User user, State state,
                           AuthorAssociation author_association, String body) {
        }
    }

    record User(String login, long id) {
    }

    record Repository(long id, String name, String full_name) {
    }

    enum Action {
        OPENED,
        CLOSED,
        REOPENED,
        SYNCHRONIZE,
        CREATED,
        DELETED,
        EDITED
    }

    enum AuthorAssociation {
        COLLABORATOR,
        CONTRIBUTOR,
        FIRST_TIMER,
        FIRST_TIME_CONTRIBUTOR,
        MANNEQUIN,
        MEMBER,
        NONE,
        OWNER
    }

    enum State {
        OPEN,
        CLOSED
    }
}
