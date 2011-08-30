package synopticgwt.client;

import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RootPanel;

import synopticgwt.client.util.ProgressWheel;

/**
 * Encapsulates functionality associated with a single tab in the application.
 */
public abstract class Tab<T extends Panel> {
    /** The service that the tab can generate calls to. */
    protected ISynopticServiceAsync synopticService;

    /** Progress indicator for this tab. */
    protected ProgressWheel pWheel;

    /** Panel with the contents of this tab. */
    protected T panel;

    public Tab(ISynopticServiceAsync synopticService, ProgressWheel pWheel) {
        this.synopticService = synopticService;
        this.pWheel = pWheel;
    }

    /**
     * Shows an error message in the rpcErrorDiv whenever an RPC call fails in a
     * Tab.
     * 
     * @param message
     *            The error message to display.
     */
    public void displayRPCErrorMessage(String message) {
        Label error = new Label(message);
        error.setStyleName("ErrorMessage");
        RootPanel.get("rpcErrorDiv").add(error);
    }

    /** This call returns the tab's Panel widget that holds the tab's contents. */
    public T getPanel() {
        return panel;
    }
}