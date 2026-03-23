package soft.shadlv.twp_rewritekts;

import java.util.EventListener;

public interface ProxyEventListener extends EventListener {
    void broadcastEvent(String event);
}