package io.papermc.proofreader.proofreader.service;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class StateService {

    // TODO persist?
    private final Map<Long, State> stateMap = new HashMap<>();

    public State getState(long number) {
        return stateMap.computeIfAbsent(number, State::new);
    }

    void updateState(State state) {
        stateMap.put(state.prNumber, state);
        System.out.println("updated state: " + state);
    }

    public static class State {
        public final long prNumber;
        public String status = "Pending";
        public @Nullable String branch = null;
        public boolean firstTimer;
        public boolean approved = false;
        public long commentId = -1;

        public State(long prNumber) {
            this.prNumber = prNumber;
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
                   '}';
        }
    }
}
