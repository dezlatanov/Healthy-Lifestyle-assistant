package bg.pu.hla.agent;

import jade.lang.acl.ACLMessage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class AgentRequestQueue {

    private final BlockingQueue<ACLMessage> inbound = new LinkedBlockingQueue<>();
    private final Map<String, PendingResponse> pending = new ConcurrentHashMap<>();

    public void enqueue(ACLMessage message) {
        inbound.offer(message);
    }

    public ACLMessage poll() {
        return inbound.poll();
    }

    public void register(String conversationId) {
        pending.put(conversationId, new PendingResponse());
    }

    public void complete(String conversationId, Map<String, Object> response) {
        PendingResponse pr = pending.get(conversationId);
        if (pr != null) {
            pr.response = response;
            pr.done = true;
            synchronized (pr) {
                pr.notifyAll();
            }
        }
    }

    public void fail(String conversationId, String error) {
        complete(conversationId, Map.of("error", error, "response", error));
    }

    public Map<String, Object> await(String conversationId, long timeoutSeconds) throws InterruptedException {
        PendingResponse pr = pending.get(conversationId);
        if (pr == null) {
            throw new IllegalStateException("Unknown conversation: " + conversationId);
        }

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        synchronized (pr) {
            while (!pr.done && System.currentTimeMillis() < deadline) {
                pr.wait(Math.max(1, deadline - System.currentTimeMillis()));
            }
        }

        pending.remove(conversationId);
        if (!pr.done) {
            throw new IllegalStateException("Agent response timeout after " + timeoutSeconds + "s");
        }
        return pr.response;
    }

    private static class PendingResponse {
        volatile boolean done;
        volatile Map<String, Object> response;
    }
}
