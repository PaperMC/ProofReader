package io.papermc.proofreader.proofreader.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StateService {

    // TODO persist?
    private final Map<Long, State> stateMap = new HashMap<>();

    private final CommentService commentService;

    public StateService(CommentService commentService) {
        this.commentService = commentService;
    }

    public State getState(long number) {
        return stateMap.computeIfAbsent(number, State::new);
    }

    public void updateState(State state) {
        commentService.addOrUpdateProofReadingComment(state);
        stateMap.put(state.prNumber, state);
        System.out.println("updated state: " + state);
    }

    public static class State {
        public final long prNumber;
        public final String branch;
        public String status = "Pending";
        public boolean firstTimer;
        public boolean approved = false;
        public long commentId = -1;
        public @Nullable String buildDir;
        public boolean completed;

        public State(long prNumber) {
            this.prNumber = prNumber;
            if (this instanceof MainState) {
                this.branch = "main";
            } else {
                this.branch = "pr/" + prNumber;
            }
        }

        @Override
        public String toString() {
            return "State{" +
                   "prNumber=" + prNumber +
                   ", status='" + status + '\'' +
                   ", branch='" + branch + '\'' +
                   ", firstTimer=" + firstTimer +
                   ", approved=" + approved +
                   ", commentId=" + commentId +
                   ", buildDir='" + buildDir + '\'' +
                   '}';
        }
    }

    public static class MainState extends State {
        public MainState() {
            super(-1);
        }
    }
}
