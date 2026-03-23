package soft.shadlv.twp_rewritekts;

import java.util.ArrayList;
import java.util.List;

public class StaticEventManager {
    private static final List<ProxyEventListener> listeners = new ArrayList<>();

    public static void addListener(ProxyEventListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(ProxyEventListener listener) {
        listeners.remove(listener);
    }

    public static void triggerEvent(String message) {
        for (ProxyEventListener listener : listeners) {
            listener.broadcastEvent(message);
        }
    }
}
