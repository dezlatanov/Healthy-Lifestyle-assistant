package bg.pu.hla.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jade.content.lang.Codec;
import jade.content.lang.sl.SLCodec;
import jade.lang.acl.ACLMessage;

import java.util.Map;

public final class AclMessageFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final Codec CODEC = new SLCodec();

    private AclMessageFactory() {
    }

    public static ACLMessage request(String receiver, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(new jade.core.AID(receiver, jade.core.AID.ISLOCALNAME));
        msg.setLanguage(CODEC.getName());
        msg.setOntology("healthy-lifestyle-requests");
        msg.setContent(content);
        return msg;
    }

    public static ACLMessage inform(String receiver, String content) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new jade.core.AID(receiver, jade.core.AID.ISLOCALNAME));
        msg.setLanguage(CODEC.getName());
        msg.setOntology("healthy-lifestyle-responses");
        msg.setContent(content);
        return msg;
    }

    public static ACLMessage failure(String receiver, String reason) {
        ACLMessage msg = new ACLMessage(ACLMessage.FAILURE);
        msg.addReceiver(new jade.core.AID(receiver, jade.core.AID.ISLOCALNAME));
        msg.setContent(reason);
        return msg;
    }

    public static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid ACL payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromJson(String content) {
        try {
            return MAPPER.readValue(content, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid ACL JSON content", e);
        }
    }
}
