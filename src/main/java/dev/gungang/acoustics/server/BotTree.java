package dev.gungang.acoustics.server;

import java.util.function.Supplier;

/**
 * Minimal behaviour tree: the utility layer decides what to do, a tree carries it out step by step. A failing step
 * fails the whole action (the caller then lets utility pick something else); a tree is rebuilt fresh for every run.
 */
final class BotTree {
    enum Status {RUNNING,SUCCESS,FAILURE}
    interface Node {Status tick();}

    static Node leaf(Supplier<Status> step) {return step::get;}
    /** Steps in order; RUNNING waits on the current step, FAILURE aborts, SUCCESS after the last step. */
    static Node sequence(Node... steps) {
        return new Node() {
            private int index;
            @Override public Status tick() {
                while(index<steps.length) {
                    var status=steps[index].tick();
                    if(status==Status.RUNNING)return status;
                    if(status==Status.FAILURE) {index=0;return status;}
                    index++;
                }
                index=0;return Status.SUCCESS;
            }
        };
    }
    /** Alternatives in order; the first that does not fail is used. */
    static Node selector(Node... options) {
        return new Node() {
            private int index;
            @Override public Status tick() {
                while(index<options.length) {
                    var status=options[index].tick();
                    if(status==Status.RUNNING)return status;
                    if(status==Status.SUCCESS) {index=0;return status;}
                    index++;
                }
                index=0;return Status.FAILURE;
            }
        };
    }
    private BotTree() { }
}
