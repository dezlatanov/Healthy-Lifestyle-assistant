package bg.pu.hla.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserProfile user;

    @Column(nullable = false)
    private String senderAgent;

    @Column(nullable = false)
    private String receiverAgent;

    @Column(nullable = false)
    private String performative;

    @Column(length = 4000)
    private String content;

    @Column(length = 4000)
    private String ontologyContext;

    private LocalDateTime timestamp;

    @PrePersist
    void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
