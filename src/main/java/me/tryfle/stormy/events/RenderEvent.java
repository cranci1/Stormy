package me.tryfle.stormy.events;

public class RenderEvent {
    public enum State {
        RENDER_2D,
        RENDER_3D
    }

    public final State state;

    public RenderEvent(State state) {
        this.state = state;
    }
}
