package me.tryfle.stormy.events;

import net.weavemc.loader.api.event.Event;
/**
 * Utility class for handling MouseEvent actions
 */
public class MouseEvent extends Event {
    private final int action;
    private final int button;
    private final int x;
    private final int y;

    public MouseEvent(int action) {
        this(action, 0, 0, 0);
    }
    
    public MouseEvent(int action, int button, int x, int y) {
        this.action = action;
        this.button = button;
        this.x = x;
        this.y = y;
    }

    public int getAction() {
        return action;
    }
    
    /**
     * Gets the mouse button that triggered this event
     * @return The button ID (0 for left, 1 for right, 2 for middle)
     */
    public int getButton() {
        return button;
    }
    
    /**
     * Gets the X coordinate of the mouse cursor
     * @return X position
     */
    public int getX() {
        return x;
    }
    
    /**
     * Gets the Y coordinate of the mouse cursor
     * @return Y position
     */
    public int getY() {
        return y;
    }
    
    /**
     * Determines whether a mouse button is being pressed or released
     * @param event The MouseEvent
     * @return true if the button is being pressed, false if released
     */
    public static boolean isButtonPressed(MouseEvent event) {
        // In Weave's MouseEvent implementation, the state is typically
        // indicated by the action code: 0 for press, 1 for release
        return event.getAction() == 0;
    }
    
    /**
     * Determines whether a mouse button is being released
     * @param event The MouseEvent
     * @return true if the button is being released, false otherwise
     */
    public static boolean isButtonReleased(MouseEvent event) {
        return event.getAction() == 1;
    }
}