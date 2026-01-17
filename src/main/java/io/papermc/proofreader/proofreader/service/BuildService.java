package io.papermc.proofreader.proofreader.service;

import org.springframework.stereotype.Service;

@Service
public class BuildService {


    private final StateService states;

    BuildService(StateService states) {
        this.states = states;
    }

    public void triggerBuild(StateService.State state) {
        state.status = "Starting Build";

        // TODO check if we have a repo, if so update, if not clone
        // TODO run gradle
        // TODO save artifacts somewhere (api, dev bundle, paperclip)
        // TODO push source

        states.updateState(state);
    }

    public void triggerRebase(StateService.State state) {
        // TODO trigger rebase
        state.status = "Starting rebase";

        states.updateState(state);
    }
}
